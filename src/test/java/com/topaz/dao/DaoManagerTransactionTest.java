package com.topaz.dao;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.topaz.common.Config;

public class DaoManagerTransactionTest {

	@Before
	public void setUp() throws Exception {
		File cfgFile = new File("src/test/resources/config-test.properties");
		Config.init(cfgFile);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testConnectionConsistency() {
		final DaoManager mgr = DaoManager.getInstance();
		mgr.useTransaction(new ITransVisitor() {
			public void visit() {
				Connection conn1 = (Connection) mgr.useConnection(new IConnVisitor() {
					public Object visit(Connection conn)
							throws SQLException {
						return conn;
					}

				});

				Connection conn2 = (Connection) mgr.useConnection(new IConnVisitor() {
					public Object visit(Connection conn)
							throws SQLException {
						return conn;
					}

				});
				boolean autoCommit = true;
				try {
					autoCommit = conn1.getAutoCommit();
				} catch (SQLException e) {
					e.printStackTrace();
				}
				Assert.assertEquals(conn1, conn2);
				Assert.assertEquals(false, autoCommit);
			}
		});
	}

	@Test
	public void testAutoCommitRecovery() {
		final DaoManager mgr = DaoManager.getInstance();
		final int numActive1 = mgr.getNumActive();
		mgr.useTransaction(new ITransVisitor() {

			public void visit() {
				Connection conn1 = (Connection) mgr.useConnection(new IConnVisitor() {
					public Object visit(Connection conn)
							throws SQLException {
						return conn;
					}
				});

				boolean autoCommit = true;
				try {
					autoCommit = conn1.getAutoCommit();
				} catch (SQLException e) {
					e.printStackTrace();
				}
				Assert.assertEquals(false, autoCommit);
				int numActive2 = mgr.getNumActive();
				Assert.assertEquals(numActive1 + 1, numActive2);
			}
		});
		int numActive3 = mgr.getNumActive();
		Assert.assertEquals(numActive1, numActive3);
	}

	@Test
	public void testTransactionInTransaction() {
		final DaoManager mgr = DaoManager.getInstance();
		mgr.useTransaction(new ITransVisitor() {
			public void visit() {
				final Connection conn1 = (Connection) mgr.useConnection(new IConnVisitor() {
					public Object visit(Connection conn)
							throws SQLException {
						return conn;
					}
				});

				mgr.useTransaction(new ITransVisitor() {
					public void visit() {
						Connection conn2 = (Connection) mgr.useConnection(new IConnVisitor() {
							public Object visit(Connection conn)
									throws SQLException {
								return conn;
							}
						});

						Assert.assertEquals(conn1, conn2);
					};
				});

				try {
					Assert.assertFalse(conn1.getAutoCommit());
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		});
	}
}
