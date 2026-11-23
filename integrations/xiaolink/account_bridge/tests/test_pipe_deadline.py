"""A blocked provider must not keep a camera request alive past its deadline."""
import os
import subprocess
import sys
import time
import unittest

from account_bridge.providers import _AcpClient, _terminate_process_group


@unittest.skipUnless(os.name == 'posix', 'The private account bridge runs on POSIX')
class PipeDeadlineTest(unittest.TestCase):
    def test_child_that_never_reads_camera_input_times_out(self):
        child = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'],
                                 stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                 stderr=subprocess.PIPE, start_new_session=True)
        client = _AcpClient(child, deadline=time.monotonic() + 0.2)
        began = time.monotonic()
        try:
            with self.assertRaises(TimeoutError):
                client.send_json({'image': 'x' * (2 * 1024 * 1024)})
            self.assertLess(time.monotonic() - began, 2)
        finally:
            client.close()
            _terminate_process_group(child)
            for pipe in (child.stdin, child.stdout, child.stderr):
                pipe.close()
