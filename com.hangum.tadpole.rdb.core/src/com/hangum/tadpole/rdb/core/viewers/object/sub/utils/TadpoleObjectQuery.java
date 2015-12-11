/*******************************************************************************
 * Copyright (c) 2013 hangum.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     hangum - initial API and implementation
 ******************************************************************************/
package com.hangum.tadpole.rdb.core.viewers.object.sub.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.hangum.tadpole.commons.dialogs.message.dao.RequestResultDAO;
import com.hangum.tadpole.commons.libs.core.define.PublicTadpoleDefine;
import com.hangum.tadpole.engine.define.DBDefine;
import com.hangum.tadpole.engine.manager.TadpoleSQLManager;
import com.hangum.tadpole.engine.query.dao.mysql.TableColumnDAO;
import com.hangum.tadpole.engine.query.dao.mysql.TableDAO;
import com.hangum.tadpole.engine.query.dao.sqlite.SQLiteForeignKeyListDAO;
import com.hangum.tadpole.engine.query.dao.system.UserDBDAO;
import com.hangum.tadpole.engine.security.DBAccessCtlManager;
import com.hangum.tadpole.engine.sql.util.ExecuteDDLCommand;
import com.hangum.tadpole.engine.sql.util.SQLUtil;
import com.hangum.tadpole.rdb.core.editors.main.utils.MakeContentAssistUtil;
import com.hangum.tadpole.tajo.core.connections.TajoConnectionManager;
import com.ibatis.sqlmap.client.SqlMapClient;

/**
 * DB Object 관련 쿼리를 뫃아 놓습니다.
 * 
 * @author hangum
 *
 */
public class TadpoleObjectQuery {
	private static Logger logger = Logger.getLogger(TadpoleObjectQuery.class);
	
	/**
	 * Rename table 
	 * 
	 * @param userDB
	 * @param tableDAO
	 * @param strRenameTable
	 * @return
	 */
	public static RequestResultDAO renameTable(final UserDBDAO userDB, TableDAO tableDAO, String strRenameTable) throws Exception {
		RequestResultDAO resultDao = null;
		if(userDB.getDBDefine() == DBDefine.MYSQL_DEFAULT | userDB.getDBDefine() == DBDefine.MARIADB_DEFAULT) {
			String strQuery = String.format("ALTER TABLE %s RENAME %s", tableDAO.getSysName(), strRenameTable);
			resultDao = ExecuteDDLCommand.executSQL(userDB, strQuery);
		} else if(userDB.getDBDefine() == DBDefine.POSTGRE_DEFAULT |
					userDB.getDBDefine() == DBDefine.ORACLE_DEFAULT |
					userDB.getDBDefine() == DBDefine.SQLite_DEFAULT
		) {
			String strQuery = String.format("ALTER TABLE %s RENAME TO %s", tableDAO.getSysName(), strRenameTable);
			resultDao = ExecuteDDLCommand.executSQL(userDB, strQuery);
		} else if(userDB.getDBDefine() == DBDefine.MSSQL_DEFAULT | userDB.getDBDefine() == DBDefine.MSSQL_8_LE_DEFAULT) {
			String strQuery = String.format("sp_rename %s, %s", tableDAO.getSysName(), strRenameTable);
			resultDao = ExecuteDDLCommand.executSQL(userDB, strQuery);
		} else if(userDB.getDBDefine() == DBDefine.CUBRID_DEFAULT) {
			String strQuery = String.format("RENAME TABLE %s AS %s", tableDAO.getSysName(), strRenameTable);
			resultDao = ExecuteDDLCommand.executSQL(userDB, strQuery);
		} else {
			throw new Exception("Not support rename table.");
		}
		
		return resultDao;
	}
	
	/**
	 * 보여 주어야할 테이블 목록을 정의합니다.
	 *
	 * @param userDB
	 * @return
	 * @throws Exception
	 */
	public static List<TableDAO> getTableList(final UserDBDAO userDB) throws Exception {
		List<TableDAO> showTables = null;
				
		if(userDB.getDBDefine() == DBDefine.TAJO_DEFAULT) {
			showTables = new TajoConnectionManager().tableList(userDB);

			// sql keyword를 설정합니다.
			if(TadpoleSQLManager.getDbMetadata(userDB) == null) {
				TadpoleSQLManager.setMetaData(TadpoleSQLManager.getKey(userDB), userDB, TajoConnectionManager.getInstance(userDB).getMetaData());
			}
			
		} else {
			SqlMapClient sqlClient = TadpoleSQLManager.getInstance(userDB);
			showTables = sqlClient.queryForList("tableList", userDB.getDb()); //$NON-NLS-1$			
		}
		
		/** filter 정보가 있으면 처리합니다. */
		return getTableAfterwork(showTables, userDB);
	}
	
	/**
	 * 선택된 Table의 컬럼 정보를 리턴합니다.
	 * 
	 * @param userDB
	 * @param tableDao
	 * @throws Exception
	 */
	public static List<TableColumnDAO> getTableColumns(UserDBDAO userDB, TableDAO tableDao) throws Exception {
		List<TableColumnDAO> returnColumns = new ArrayList<TableColumnDAO>();
		
		Map<String, String> mapParam = new HashMap<String, String>();
		mapParam.put("db", userDB.getDb());
		String strTableName = "";
		if(userDB.getDBDefine() == DBDefine.SQLite_DEFAULT) strTableName = tableDao.getSysName();
		else 												strTableName = tableDao.getName();
		
		mapParam.put("schema", tableDao.getSchema_name());
		mapParam.put("table", strTableName);

		if(userDB.getDBDefine() != DBDefine.TAJO_DEFAULT) {
			SqlMapClient sqlClient = TadpoleSQLManager.getInstance(userDB);
			returnColumns = sqlClient.queryForList("tableColumnList", mapParam); //$NON-NLS-1$
		} else {
			returnColumns = new TajoConnectionManager().tableColumnList(userDB, mapParam);
		}
		
		if(DBDefine.SQLite_DEFAULT == userDB.getDBDefine()){
			try{
				SqlMapClient sqlClient = TadpoleSQLManager.getInstance(userDB);
				List<SQLiteForeignKeyListDAO> foreignKeyList = sqlClient.queryForList("tableForeignKeyList", mapParam); //$NON-NLS-1$
				for (SQLiteForeignKeyListDAO fkeydao : foreignKeyList){
					for (TableColumnDAO dao : returnColumns){
						if (dao.getName().equals(fkeydao.getFrom() ) ){
							if (PublicTadpoleDefine.isPK(dao.getKey())){
								dao.setKey("MUL");
							}else{
								dao.setKey("FK");
							}
						}	 
					}
				}
			}catch(Exception e){
				logger.error("not found foreignkey for " + tableDao.getName());
			}
		}
		
		// if find the keyword is add system quote.
		for(TableColumnDAO td : returnColumns) {
			td.setTableDao(tableDao);
			td.setSysName(SQLUtil.makeIdentifierName(userDB, td.getField()));
		}
		
		returnColumns = DBAccessCtlManager.getInstance().getColumnFilter(tableDao, returnColumns, userDB);
		
		return returnColumns;
	}

	
	/**
	 * Table 정보 처리 후에 
	 * 
	 * @param showTables
	 * @param userDB
	 * @return
	 */
	private static List<TableDAO> getTableAfterwork(List<TableDAO> showTables, final UserDBDAO userDB) {
		final StringBuffer strTablelist = new StringBuffer();
		/** filter 정보가 있으면 처리합니다. */
		showTables = DBAccessCtlManager.getInstance().getTableFilter(showTables, userDB);
		
		// 시스템에서 사용하는 용도록 수정합니다. '나 "를 붙이도록.
		for(TableDAO td : showTables) {
			td.setSysName(SQLUtil.makeIdentifierName(userDB, td.getName()));
			
			strTablelist.append(td.getSysName()).append("|"); //$NON-NLS-1$
		}
		
		// setting UserDBDAO 
		userDB.setListTable(showTables);

		// content assist util
		MakeContentAssistUtil contentAssistUtil = new MakeContentAssistUtil();
		contentAssistUtil.makeContentAssistUtil(userDB);
		
		return showTables;
	}
	
	/**
	 * @param userDB
	 * @param strObject
	 * @return
	 */
	public static TableDAO getTable(UserDBDAO userDB, String strObject) throws Exception {
		TableDAO tableDao = null;
		List<TableDAO> showTables = new ArrayList<TableDAO>();
		
		if(userDB.getDBDefine() == DBDefine.TAJO_DEFAULT) {
			List<TableDAO> tmpShowTables = new TajoConnectionManager().tableList(userDB);
			
			for(TableDAO dao : tmpShowTables) {
				if(dao.getName().equals(strObject)) {
					showTables.add(dao);
					break;
				}
			}
		} else if(userDB.getDBDefine() == DBDefine.HIVE_DEFAULT | userDB.getDBDefine() == DBDefine.HIVE2_DEFAULT) {
			SqlMapClient sqlClient = TadpoleSQLManager.getInstance(userDB);
			List<TableDAO> tmpShowTables = sqlClient.queryForList("tableList", userDB.getDb()); //$NON-NLS-1$
			
			for(TableDAO dao : tmpShowTables) {
				if(dao.getName().equals(strObject)) {
					showTables.add(dao);
					break;
				}
			}
			
		} else {
			Map<String, Object> mapParam = new HashMap<String, Object>();
			mapParam.put("db", 	userDB.getDb()); //$NON-NLS-1$
			mapParam.put("name", 	strObject); //$NON-NLS-1$
			
			SqlMapClient sqlClient = TadpoleSQLManager.getInstance(userDB);
			showTables = sqlClient.queryForList("table", mapParam); //$NON-NLS-1$			
		}
		
		/** filter 정보가 있으면 처리합니다. */
		showTables = DBAccessCtlManager.getInstance().getTableFilter(showTables, userDB);
		
		if(!showTables.isEmpty()) { 
			tableDao = showTables.get(0);
			tableDao.setSysName(SQLUtil.makeIdentifierName(userDB, tableDao.getName()));
			return tableDao;
		} else {
			return null;
		}
	}
}