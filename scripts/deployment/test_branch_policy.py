import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import branch_policy as policy


class BranchPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / 'repo'
        self.repo.mkdir()
        self.run_git('init', '--quiet', '--initial-branch=develop')
        self.run_git('config', 'user.name', 'Deployment test fixture')
        self.run_git('config', 'user.email', 'fixture@example.invalid')
        self.run_git('config', 'core.autocrlf', 'false')
        self.origin = self.root / 'origin.git'
        subprocess.run(['git', 'init', '--quiet', '--bare', str(self.origin)], check=True, capture_output=True)
        self.run_git('remote', 'add', 'origin', str(self.origin))
        self.base = self.commit('base')
        self.run_git('branch', 'main')
        self.run_git('push', '--quiet', 'origin', 'develop', 'main')
        self.cwd = os.getcwd()
        os.chdir(self.repo)
        self.addCleanup(os.chdir, self.cwd)

    def run_git(self, *args):
        result = subprocess.run(['git', *args], cwd=self.repo, check=True, capture_output=True, text=True)
        return result.stdout.strip()

    def commit(self, content, filename='application.txt'):
        (self.repo / filename).write_text(content, encoding='utf-8')
        self.run_git('add', '--', filename)
        self.run_git('commit', '--quiet', '-m', 'Deployment fixture')
        return self.run_git('rev-parse', 'HEAD')

    def snapshot(self, release=None):
        return {'releaseId': release or self.base, 'jarSha256': 'a' * 64}

    def evaluate(self, branch, commit, release=None):
        return policy.evaluate('branch', branch, commit, self.snapshot(release))

    def test_forward_develop_then_main_deployments(self):
        updated = self.commit('updated')
        self.run_git('push', '--quiet', 'origin', 'develop')
        self.assertEqual(self.evaluate('develop', updated)['deploy'], 'true')
        self.run_git('switch', '--quiet', 'main')
        self.run_git('merge', '--quiet', '--no-ff', 'develop', '-m', 'Promote fixture')
        promoted = self.run_git('rev-parse', 'HEAD')
        self.run_git('push', '--quiet', 'origin', 'main')
        result = self.evaluate('main', promoted, updated)
        self.assertEqual(result['deploy'], 'true')
        self.assertEqual(result['expected_release'], updated)
        self.assertEqual(result['expected_jar'], 'a' * 64)

    def test_merge_only_main_does_not_block_newer_develop_with_same_baseline(self):
        prior = self.commit('prior')
        self.run_git('switch', '--quiet', 'main')
        self.run_git('merge', '--quiet', '--no-ff', 'develop', '-m', 'Promote fixture')
        promoted = self.run_git('rev-parse', 'HEAD')
        self.run_git('switch', '--quiet', 'develop')
        updated = self.commit('next')
        self.run_git('push', '--quiet', 'origin', 'develop', 'main')
        self.assertNotEqual(prior, promoted)
        self.assertNotEqual(self.run_git('merge-base', promoted, updated), promoted)
        self.assertEqual(self.evaluate('develop', updated, promoted)['deploy'], 'true')

    def test_older_main_cannot_roll_back_a_develop_release(self):
        updated = self.commit('new code')
        with self.assertRaisesRegex(policy.PolicyError, 'LIVE_CODE_NOT_INCLUDED_SYNC_BRANCH_FIRST'):
            self.evaluate('main', self.base, updated)

    def test_main_hotfix_cannot_be_lost_and_sync_unblocks_develop(self):
        updated = self.commit('develop change')
        self.run_git('switch', '--quiet', 'main')
        hotfix = self.commit('main hotfix', 'security.txt')
        self.run_git('push', '--quiet', 'origin', 'develop', 'main')
        with self.assertRaisesRegex(policy.PolicyError, 'LIVE_CODE_NOT_INCLUDED_SYNC_BRANCH_FIRST'):
            self.evaluate('develop', updated, hotfix)
        self.run_git('switch', '--quiet', 'develop')
        self.run_git('merge', '--quiet', '--no-ff', 'main', '-m', 'Sync hotfix fixture')
        synced = self.run_git('rev-parse', 'HEAD')
        self.run_git('push', '--quiet', 'origin', 'develop')
        self.assertEqual(self.evaluate('develop', synced, hotfix)['deploy'], 'true')

    def test_entire_tree_includes_migrations_and_workflow_not_just_java(self):
        self.run_git('switch', '--quiet', 'main')
        protected = self.commit('migration and policy change', 'migration.sql')
        self.run_git('switch', '--quiet', 'develop')
        candidate = self.commit('another change')
        self.run_git('push', '--quiet', 'origin', 'develop', 'main')
        with self.assertRaisesRegex(policy.PolicyError, 'LIVE_CODE_NOT_INCLUDED_SYNC_BRANCH_FIRST'):
            self.evaluate('develop', candidate, protected)

    def test_same_release_is_allowed_for_existing_idempotent_server_check(self):
        self.assertEqual(self.evaluate('main', self.base)['deploy'], 'true')

    def test_superseded_main_and_develop_are_skipped(self):
        candidate = self.commit('first')
        newer = self.commit('second')
        self.run_git('branch', '--force', 'main', newer)
        self.run_git('push', '--quiet', 'origin', 'develop', 'main')
        for branch in ('develop', 'main'):
            self.assertEqual(self.evaluate(branch, candidate), {'deploy':'false', 'reason':'SUPERSEDED_BRANCH_COMMIT'})

    def test_invalid_refs_and_shas_are_rejected_before_git_or_output(self):
        for ref_type, branch, sha in [('tag','main',self.base), ('branch','feature/test',self.base),
                                      ('branch','main','bad'), ('branch','main','A'*40),
                                      ('branch','main','a'*39+';')]:
            with self.subTest(ref_type=ref_type, branch=branch, sha=sha):
                with patch.object(policy, 'git') as runner:
                    with self.assertRaises(policy.PolicyError):
                        policy.evaluate(ref_type, branch, sha, self.snapshot())
                    runner.assert_not_called()

    def test_invalid_snapshot_cannot_be_used_as_actions_output(self):
        for snapshot in [[], {}, {'releaseId':self.base,'jarSha256':'bad'},
                         {'releaseId':self.base,'jarSha256':'a'*64+'\ndeploy=true'},
                         {**self.snapshot(), 'deploy':'true'}]:
            with self.assertRaisesRegex(policy.PolicyError, 'INVALID_LIVE_SNAPSHOT'):
                policy.evaluate('branch','main',self.base,snapshot)

    def test_missing_remote_and_unknown_live_commit_fail_closed(self):
        with self.assertRaises(policy.PolicyError):
            self.evaluate('main',self.base,'0'*40)
        self.run_git('remote', 'remove', 'origin')
        with self.assertRaisesRegex(policy.PolicyError,'GIT_QUERY_FAILED'):
            self.evaluate('main',self.base)

    def test_shallow_history_and_remote_query_failure_fail_closed(self):
        original = policy.git
        def shallow(*args):
            return 'true' if args == ('rev-parse','--is-shallow-repository') else original(*args)
        with patch.object(policy,'git',side_effect=shallow):
            with self.assertRaisesRegex(policy.PolicyError,'FULL_GIT_HISTORY_REQUIRED'):
                self.evaluate('main',self.base)
        with patch.object(policy.subprocess,'run',side_effect=subprocess.TimeoutExpired('git',60)):
            with self.assertRaisesRegex(policy.PolicyError,'GIT_QUERY_FAILED'):
                self.evaluate('main',self.base)

    def test_cli_outputs_only_fixed_safe_fields_and_sanitizes_failures(self):
        snapshot = self.root / 'snapshot.json'
        snapshot.write_text(json.dumps(self.snapshot()),encoding='utf-8')
        args = ['policy','--ref-type','branch','--branch','main','--candidate',self.base,'--snapshot',str(snapshot)]
        stdout,stderr=io.StringIO(),io.StringIO()
        with patch.object(sys,'argv',args),contextlib.redirect_stdout(stdout),contextlib.redirect_stderr(stderr):
            self.assertEqual(policy.main(),0)
        self.assertIn('deploy=true\n',stdout.getvalue())
        snapshot.write_text('private invalid snapshot',encoding='utf-8')
        stdout,stderr=io.StringIO(),io.StringIO()
        with patch.object(sys,'argv',args),contextlib.redirect_stdout(stdout),contextlib.redirect_stderr(stderr):
            self.assertEqual(policy.main(),1)
        self.assertEqual(stdout.getvalue(),'')
        self.assertEqual(stderr.getvalue().strip(),'LIVE_SNAPSHOT_UNREADABLE')


if __name__ == '__main__':
    unittest.main()
