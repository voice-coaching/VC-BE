from pathlib import Path
import re
import unittest

ROOT=Path(__file__).resolve().parents[2]


class WorkflowContractTest(unittest.TestCase):
    def test_both_branches_share_serial_non_canceling_production_queue(self):
        text=(ROOT/'.github/workflows/deploy.yml').read_text(encoding='utf-8')
        self.assertRegex(text,r'push:\s*\n\s+branches: \[develop, main\]')
        self.assertIn("github.ref_type == 'branch' && (github.ref_name == 'develop' || github.ref_name == 'main')",text)
        section=re.search(r'^concurrency:\n(.*?)(?=^\S)',text,re.M|re.S).group(1)
        config=dict(re.findall(r'^  ([\w-]+): ([^\n]+)$',section,re.M))
        # actionlint 1.7.12 does not recognize this documented GitHub queue field.
        # Explicitly validate its complete value/combination; other lint is not waived.
        self.assertEqual(config,{'group':'production-deploy','cancel-in-progress':'false','queue':'max'})
        self.assertIn('environment: production',text)

    def test_exact_commit_is_tested_before_credentials_and_activation(self):
        text=(ROOT/'.github/workflows/deploy.yml').read_text(encoding='utf-8')
        self.assertIn('fetch-depth: 0',text)
        self.assertIn('./gradlew clean test bootJar --no-daemon',text)
        self.assertNotIn('-x test',text)
        self.assertLess(text.index('Test and package exact release commit'),text.index('Configure SSH'))
        self.assertLess(text.index('Read committed live release'),text.index('Check latest branch commit'))
        self.assertLess(text.index('Check latest branch commit'),text.index('name: Activate release'))
        self.assertIn("if: steps.policy.outputs.deploy == 'true'",text)
        self.assertIn("'$EXPECTED_RELEASE' '$EXPECTED_JAR'",text)
        self.assertIn('steps.upload.outcome',text)

    def test_main_prs_and_deployment_policy_are_in_ci(self):
        text=(ROOT/'.github/workflows/ci.yml').read_text(encoding='utf-8')
        self.assertRegex(text,r'pull_request:\s*\n\s+branches: \[develop, main\]')
        self.assertIn("unittest discover -s scripts/deployment -p 'test_*.py'",text)
        self.assertIn('./gradlew clean test bootJar --no-daemon',text)


if __name__=='__main__':unittest.main()
