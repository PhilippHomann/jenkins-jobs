// Merge a number of branches of webapp, and push the merge commit to github.
//
// This is used by the buildmaster -- such that the rest of Jenkins, for the
// most part, can only ever worry about fixed SHAs, and never have to worry
// about making the right merge commit or letting the buildmaster know about
// it.

@Library("kautils")
// Classes we use, under jenkins-jobs/src/.
import org.khanacademy.Setup;
// Vars we use, under jenkins-jobs/vars/.  This is just for documentation.
//import vars.exec
//import vars.kaGit
//import vars.notify
//import vars.withTimeout

new Setup(steps
).addStringParam(
    "GIT_REVISIONS",
    """<b>REQUIRED</b>. A plus-separated list of commit-ishes to merge, like
"master + yourbranch + mybranch + sometag + deadbeef1234".""",
    ""
).addStringParam(
    "DEPLOY_ID",
    """<b>REQUIRED</b>. The buildmaster's deploy ID for this merge.""",
    ""
).apply();

currentBuild.displayName = "${currentBuild.displayName} (${params.GIT_REVISIONS})";


def mergeBranches() {
   def allBranches = params.GIT_REVISIONS.split(/\+/);
   for (def i = 0; i < allBranches.size(); i++) {
      // TODO(benkraft): Send to the right person/channel in Slack and the
      // buildmaster if the a commit-ish is invalid, or if we fail to merge.
      def sha1 = kaGit.resolveCommitish("git@github.com:Khan/webapp",
                                        allBranches[i].trim());
      // We don't use kaGit here, because we don't need to update submodules at
      // each step, and we don't need a fully clean checkout.  All we need is
      // enough to merge.  This saves us a *lot* of time traversing all the
      // submodules on each branch, and being careful to clean at each step.
      exec(["git", "fetch", "--prune", "--tags", "--progress", "origin"]);
      if (i == 0) {
         // TODO(benkraft): If there's only one branch, skip the checkout and
         // tag/return sha1 immediately.
         exec(["git", "checkout", "-f", sha1]);
      } else {
         exec(["git", "merge", sha1]);
      }
   }
   // We need to at least tag the commit, otherwise github may prune it.
   tag_name = ("buildmaster-${params.DEPLOY_ID}-" +
               "${new Date().format('yyyyMMdd-HHmmss')}");
   exec(["git", "tag", tag_name, "HEAD"]);
   exec(["git", "push", "--tags", "origin"]);
   // STOPSHIP(benkraft): Send result back to buildmaster!
}

// STOPSHIP(benkraft): Update once we are sending back to the buildmaster.
notify([slack: [channel: '#bot-testing',  // STOPSHIP
                sender: 'Mr Monkey',
                emoji: ':monkey_face:',
                when: ['SUCCESS', 'FAILURE', 'UNSTABLE', 'ABORTED']],
        aggregator: [initiative: 'infrastructure',
                     when: ['SUCCESS', 'BACK TO NORMAL',
                            'FAILURE', 'ABORTED', 'UNSTABLE']],
        timeout: "5m"]) {
   withTimeout('5m') {
      mergeBranches();
   }
}
