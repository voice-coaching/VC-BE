from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class RetentionWorkflowTest(unittest.TestCase):
    def test_cleanup_follows_activation_and_precedes_upload_removal(self):
        text = (ROOT / '.github/workflows/deploy.yml').read_text(encoding='utf-8')
        marker = 'Retain live JAR and fifteen rollback versions after healthy startup'
        self.assertLess(text.index('name: Activate release'), text.index(marker))
        self.assertLess(text.index(marker), text.index('name: Remove this run'))
        section = text[text.index(marker):text.index('name: Remove this run')]
        self.assertIn("--apply --expected-release '$GITHUB_SHA' --expected-jar-sha '$JAR_SHA'", section)
        self.assertNotIn('continue-on-error', section)
        self.assertIn('scp -r -i ~/.ssh/alpha-deploy scripts/deployment', text)

    def test_deployments_share_a_non_canceling_queue(self):
        text = (ROOT / '.github/workflows/deploy.yml').read_text(encoding='utf-8')
        section = re.search(r'^concurrency:\n(.*?)(?=^\S)', text, re.M | re.S).group(1)
        self.assertIn('group: production-deploy', section)
        self.assertIn('cancel-in-progress: false', section)
        self.assertNotIn('github.ref', section)

    def test_retention_regression_runs_before_credentials_in_ci_and_cd(self):
        for name in ['ci.yml', 'deploy.yml']:
            text = (ROOT / '.github/workflows' / name).read_text(encoding='utf-8')
            self.assertIn("unittest discover -s scripts/deployment -p 'test_*retention*.py'", text)
            self.assertIn('actions/setup-python@v6', text)
            if name == 'deploy.yml':
                self.assertLess(text.index('unittest discover'), text.index('name: Configure SSH'))


if __name__ == '__main__':
    unittest.main()
