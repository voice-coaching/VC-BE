import importlib.util,json,os,sqlite3,tempfile,unittest
from pathlib import Path
from contextlib import closing
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('observability_operator',Path(__file__).with_name('operator.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class OperatorFileTests(unittest.TestCase):
    def test_modern_dashboard_registration_and_existing_objects_are_verified(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'grafana.db'
            own={'spec':{'title':'Managed','elements':{'p'+str(i):{'kind':'Panel'} for i in range(8)}}}
            with closing(sqlite3.connect(path)) as db:
                db.execute('CREATE TABLE dashboard(uid TEXT,title TEXT,data TEXT,org_id INTEGER)')
                db.execute('CREATE TABLE data_source(uid TEXT,name TEXT,type TEXT,org_id INTEGER)')
                db.execute('CREATE TABLE resource("group" TEXT,resource TEXT,namespace TEXT,name TEXT,value TEXT)')
                db.execute('INSERT INTO resource VALUES(?,?,?,?,?)',('dashboard.grafana.app','dashboards','default',module.UID,json.dumps(own)))
                db.execute('INSERT INTO resource VALUES(?,?,?,?,?)',('dashboard.grafana.app','dashboards','default','existing',json.dumps({'spec':{'title':'Existing'}})))
                db.commit()
            with patch.object(module,'DB',path):
                self.assertEqual(module.panel_count(module.dashboard_document()),8)
                before=module.metadata()
                with closing(sqlite3.connect(path)) as db:
                    db.execute('UPDATE resource SET value=? WHERE name=?',(json.dumps({'spec':{'title':'Managed updated'}}),module.UID));db.commit()
                self.assertEqual(module.metadata(),before)
                with closing(sqlite3.connect(path)) as db:
                    db.execute('UPDATE resource SET value=? WHERE name=?',(json.dumps({'spec':{'title':'Existing changed'}}),'existing'));db.commit()
                self.assertNotEqual(module.metadata(),before)
    def test_wal_pressure_blocks_changes_even_when_ready_endpoint_is_green(self):
        self.assertEqual(module.writable_wal_disk('loki_ingester_wal_disk_usage_percent 0.87\n'),0.87)
        for value in ('0.90','0.971','NaN','-1'):
            with self.subTest(value=value),self.assertRaisesRegex(RuntimeError,'LOKI_WAL_DISK_PRESSURE'):
                module.writable_wal_disk('loki_ingester_wal_disk_usage_percent '+value+'\n')
        with self.assertRaisesRegex(RuntimeError,'LOKI_WAL_DISK_METRIC_REQUIRED'):module.writable_wal_disk('')
    def test_in_place_update_and_rollback_preserve_inode_and_remove_old_tail(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'config.alloy';path.write_bytes(b'original longer configuration')
            before=path.stat().st_ino
            fd=os.open(path,os.O_RDWR)
            try:
                module.replace_contents(fd,b'new')
                self.assertEqual(path.read_bytes(),b'new');self.assertEqual(path.stat().st_ino,before)
                module.replace_contents(fd,b'original longer configuration')
                self.assertEqual(path.read_bytes(),b'original longer configuration');self.assertEqual(path.stat().st_ino,before)
            finally:os.close(fd)
    @unittest.skipUnless(hasattr(os,'O_NOFOLLOW'),'Requires native Linux directory descriptor flags')
    def test_source_symlinks_and_symlink_parents_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);actual=root/'actual';actual.mkdir();(actual/'config').write_text('fixture')
            (root/'link').symlink_to(actual,target_is_directory=True)
            (root/'filelink').symlink_to(actual/'config')
            for path in (root/'link/config',root/'filelink'):
                with self.assertRaises(OSError):module.read_fd(path)
    def test_grafana_inspection_opens_sqlite_read_only(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'grafana.db'
            with closing(sqlite3.connect(path)) as db:
                db.execute('CREATE TABLE fixture(value TEXT)');db.commit()
            with patch.object(module,'DB',path):
                self.assertEqual(module.db_query('SELECT count(*) FROM fixture'),[(0,)])
                with self.assertRaises(sqlite3.OperationalError):module.db_query("INSERT INTO fixture VALUES('unexpected')")

if __name__=='__main__':unittest.main()
