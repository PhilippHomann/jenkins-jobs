// The pipeline job to update dev/ownership_data.json.
//
// This file contains the mapping of who owns what; see dev/ownership.py for
// details.  This job updates the file automatically, checks it into git, and
// uploads it to GCS.

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
   "GIT_REVISION",
   "A commit-ish to check out to update the data file at.",
   "master"

).addStringParam(
   "SLACK_CHANNEL",
   "The slack channel to send our status info.",
   "#infrastructure"

).addCronSchedule(
   '0 3 * * *'

).apply();


def runScript() {
   // Set up deps
   kaGit.safeSyncToOrigin("git@github.com:Khan/webapp", params.GIT_REVISION);

   dir("webapp") {
      sh("make clean_pyc");    // in case some .py files went away
      sh("make deps");

      // Run the script!
      sh("dev/tools/update_ownership_data.py");
   }
}


def publishResults() {
   // Get ready to overwrite a file in our repo.  We do this in the
   // 'automated-commits' branch.
   kaGit.safePullInBranch("webapp", "automated-commits");
   // ...which we want to make sure is up-to-date with master.
   kaGit.safeMergeFromMaster("webapp", "automated-commits");

   dir("webapp") {
      sh("git add dev/ownership_data.json");
      // Also publish to GCS, for usage from scripts.
      // TODO(benkraft): Is this actually the best way for scripts to read this
      // data?
      sh("gsutil cp dev/ownership_data.json "
         + "gs://webapp-artifacts/ownership_data.json");
   }
   // Check it in!
   kaGit.safeCommitAndPush(
      "webapp", ["-m", "Automated update of ownership_data.json"]);
}


onMaster('2h') {
   notify([slack: [channel: params.SLACK_CHANNEL,
                   sender: 'Testing Turtle',
                   emoji: ':turtle:',
                   when: ['FAILURE', 'UNSTABLE', 'ABORTED']],
           aggregator: [initiative: 'infrastructure',
                        when: ['BACK TO NORMAL',
                               'FAILURE', 'ABORTED', 'UNSTABLE']]]) {
      stage("Run script") {
         runScript();
      }
      stage("Publish results") {
         publishResults();
      }
   }
}
