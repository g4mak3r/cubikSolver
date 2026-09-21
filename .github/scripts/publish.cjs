const fs = require('fs');
const crypto = require('crypto');

module.exports = async ({github, context, core}) => {
  const repo = context.repo;
  const {data: main} = await github.rest.git.getRef({...repo, ref: 'heads/main'});
  if (main.object.sha !== context.sha) {
    core.info('A newer commit is on main; its successful run will publish.');
    return;
  }
  const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');
  const version = gradle.match(/versionName = "([^"]+)"/)[1];
  const tag = `v${version}`;
  let release;
  try {
    release = (await github.rest.repos.getReleaseByTag({...repo, tag})).data;
  } catch (error) {
    if (error.status !== 404) throw error;
    release = (await github.rest.repos.createRelease({...repo, tag_name: tag,
      target_commitish: context.sha, name: `cubikSolver ${version}`, draft: true,
      body: 'Unified native Android design, offline 3×3 / 5×5 solving, and corrected UI test synchronization.\n\nBuild, solver unit tests and all six native UI checks passed before publication.\n\nDownload the arm64 debug APK below (Android 10+). This is a development build, not a Play Store release.\n\nCI: https://github.com/' + repo.owner + '/' + repo.repo + '/actions/runs/' + context.runId
    })).data;
  }
  if (release.draft) {
    const apk = fs.readFileSync('release-apk/app-debug.apk');
    const name = `cubikSolver-${version}-arm64-debug.apk`;
    const checksum = Buffer.from(crypto.createHash('sha256').update(apk).digest('hex') + '  ' + name + '\n');
    const assets = (await github.rest.repos.listReleaseAssets({...repo, release_id: release.id})).data;
    for (const [filename, data, contentType] of [[name, apk, 'application/vnd.android.package-archive'], ['SHA256SUMS.txt', checksum, 'text/plain']]) {
      const old = assets.find(asset => asset.name === filename);
      if (old) await github.rest.repos.deleteReleaseAsset({...repo, asset_id: old.id});
      await github.rest.repos.uploadReleaseAsset({...repo, release_id: release.id,
        name: filename, data, headers: {'content-type': contentType, 'content-length': data.length}});
    }
    await github.rest.repos.updateRelease({...repo, release_id: release.id, draft: false, make_latest: 'true'});
  }
  core.info(`Published ${release.html_url}`);

  // One-time, reviewed branch snapshot. Never delete a branch that has moved.
  const branches = JSON.parse(fs.readFileSync('.github/branch-cleanup.json', 'utf8'));
  for (const branch of branches) {
    if (!branch.name || branch.name === 'main') throw new Error('Invalid cleanup entry');
    let ref;
    try { ref = (await github.rest.git.getRef({...repo, ref: `heads/${branch.name}`})).data; }
    catch (error) { if (error.status === 404) continue; throw error; }
    if (ref.object.sha !== branch.sha) { core.warning(`Kept changed branch: ${branch.name}`); continue; }
    if (branch.archive) {
      const archive = `tags/archive/2026-09-21/${branch.name}`;
      try { await github.rest.git.createRef({...repo, ref: `refs/${archive}`, sha: branch.sha}); }
      catch (error) {
        if (error.status !== 422) throw error;
        const saved = (await github.rest.git.getRef({...repo, ref: archive})).data;
        if (saved.object.sha !== branch.sha) throw new Error(`Archive mismatch: ${branch.name}`);
      }
    } else {
      const comparison = (await github.rest.repos.compareCommitsWithBasehead({...repo, basehead: `${branch.sha}...${context.sha}`})).data;
      if (comparison.merge_base_commit.sha !== branch.sha) throw new Error(`Branch is not merged: ${branch.name}`);
    }
    await github.rest.git.deleteRef({...repo, ref: `heads/${branch.name}`});
    core.info(`Removed branch: ${branch.name}`);
  }
};
