"""Pull request size labels and the approval gate."""
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('pr_size', Path(__file__).resolve().parents[1] / 'tools/pr-size.py')
pr_size = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pr_size)


def review(login, state, commit='head', association='MEMBER', kind='User'):
    return {'user': {'login': login, 'type': kind}, 'state': state, 'commit_id': commit,
            'author_association': association}


class SizeTests(unittest.TestCase):
    def test_boundaries(self):
        for lines, label in ((0, 'size:XS'), (9, 'size:XS'), (10, 'size:S'), (29, 'size:S'),
                             (30, 'size:M'), (99, 'size:M'), (100, 'size:L'), (499, 'size:L'),
                             (500, 'size:XL'), (999, 'size:XL'), (1000, 'size:XXL')):
            with self.subTest(lines=lines):
                self.assertEqual(label, pr_size.size_label(lines))

    def test_threshold(self):
        self.assertFalse(pr_size.needs_approval('size:M', 'size:L'))
        self.assertTrue(pr_size.needs_approval('size:L', 'size:L'))
        self.assertTrue(pr_size.needs_approval('size:XXL', 'size:L'))

    def test_numstat_counts_both_sides_and_skips_binaries(self):
        self.assertEqual(12, pr_size.sum_numstat('3\t4\ta.java\n-\t-\tlogo.png\n5\t0\tb.yml\n'))

    def test_tests_count_only_in_test_only_pull_requests(self):
        self.assertEqual(40, pr_size.effective_lines(400, 40))
        self.assertEqual(400, pr_size.effective_lines(400, 0))


class ApprovalTests(unittest.TestCase):
    def test_author_bots_outsiders_and_comments_do_not_approve(self):
        reviews = [review('author', 'APPROVED'), review('coderabbitai[bot]', 'APPROVED', kind='Bot'),
                   review('stranger', 'APPROVED', association='NONE'), review('dref', 'COMMENTED')]
        self.assertEqual([], pr_size.approvers(reviews, 'author', 'head'))

    def test_latest_decisive_review_on_current_head_counts(self):
        reviews = [review('dref', 'APPROVED'), review('dref', 'COMMENTED'),
                   review('justin', 'APPROVED'), review('justin', 'CHANGES_REQUESTED'),
                   review('fran', 'APPROVED', commit='old'), review('nowko', 'DISMISSED')]
        self.assertEqual(['dref'], pr_size.approvers(reviews, 'author', 'head'))


class MeasureTests(unittest.TestCase):
    def test_diff_ignores_whitespace_and_mixed_tests(self):
        with tempfile.TemporaryDirectory() as directory:
            def git(*args):
                return subprocess.run(['git', '-C', directory, *args], check=True,
                                      capture_output=True, text=True).stdout.strip()

            def write(path, text):
                target = Path(directory, path)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(text)

            git('init', '-q', '-b', 'main')
            git('config', 'user.email', 'test@example.test')
            git('config', 'user.name', 'Test')
            write('src/main/java/A.java', 'class A {\nint a;\n}\n')
            git('add', '.')
            git('commit', '-qm', 'base')
            base = git('rev-parse', 'HEAD')
            write('src/main/java/A.java', 'class A {\n    int a;\n\n    int b;\n}\n')
            write('src/test/java/ATest.java', 'class ATest {\n}\n')
            git('add', '.')
            git('commit', '-qm', 'change')
            head = git('rev-parse', 'HEAD')

            cwd = os.getcwd()
            os.chdir(directory)
            try:
                self.assertEqual((4, 2), pr_size.measure(base, head))
            finally:
                os.chdir(cwd)


if __name__ == '__main__':
    unittest.main()
