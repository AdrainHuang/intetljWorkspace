package org.smart4j.framework.helper;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smart4j.framework.util.CollectionUtil;
import org.smart4j.framework.util.PropsUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Created by AdrainHuang on 2017/1/28.
 */
public class DatabaseHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseHelper.class);

    private static final QueryRunner QUERY_RUNNER;
    //本地线程变量
    private static final ThreadLocal<Connection> CONNECTION_HOLDER;

    private static final BasicDataSource DATA_SOURCE;


    static {
        QUERY_RUNNER = new QueryRunner();
        CONNECTION_HOLDER = new ThreadLocal<Connection>();
        Properties conf = PropsUtil.loadProps("config.properties");
        String driver = conf.getProperty("jdbc.driver");
        String url = conf.getProperty("jdbc.url");
        String username = conf.getProperty("jdbc.username");
        String password = conf.getProperty("jdbc.password");

        DATA_SOURCE = new BasicDataSource();
        DATA_SOURCE.setDriverClassName(driver);
        DATA_SOURCE.setUrl(url);
        DATA_SOURCE.setUsername(username);
        DATA_SOURCE.setPassword(password);
    }

    /**
     * 获取数据库连接
     */
    public static Connection getConnection(){
        Connection conn = CONNECTION_HOLDER.get();
        if(conn == null){
            try {
                conn = DATA_SOURCE.getConnection();
            } catch (SQLException e) {
                LOGGER.error("get connection failure",e);
                throw new RuntimeException(e);
            } finally {
                CONNECTION_HOLDER.set(conn);
            }
        }
        return conn;
    }

//    /**
//     * 关闭数据库连接
//     */
//    public static void closeConnection(){
//        Connection conn = CONNECTION_HOLDER.get();
//        if(conn != null){
//            try {
//                conn.close();
//            } catch (SQLException e) {
//                LOGGER.error("close connection failure",e);
//                throw new RuntimeException(e);
//            } finally {
//                CONNECTION_HOLDER.remove();
//            }
//        }
//    }

    /**
     * 查询实例列表
     */
    public static <T> List<T> queryEntityList(Class<T> entityClass,String sql, Object ... params){
        List<T> entityList;
        try {
            Connection conn = getConnection();
            entityList = QUERY_RUNNER.query(conn,sql,new BeanListHandler<T>(entityClass),params);
        } catch (SQLException e){
            LOGGER.error("query entity list failure",e);
            throw new RuntimeException(e);
        }
        return entityList;
    }

    /**
     * 查询实体
     */
    public static <T> T queryEntity(Class<T> entityClass, String sql, Object ... params){
        T entity;
        try {
            Connection conn = getConnection();
            entity = QUERY_RUNNER.query(conn,sql,new BeanHandler<T>(entityClass),params);
        } catch (SQLException e){
            LOGGER.error("query entity list failure",e);
            throw new RuntimeException(e);
        }
        return entity;
    }

    /**
     * 执行查询语句
     */
    public static List<Map<String, Object>> executeQuery(String sql, Object ... params){
        List<Map<String ,Object>> result;
        try {
            Connection conn = getConnection();
            result = QUERY_RUNNER.query(conn,sql,new MapListHandler(),params);
        } catch (SQLException e) {
            LOGGER.error("execute query failure", e);
            throw new RuntimeException(e);
        }
        return result;
    }

    /**
     * 执行更新语句（包括update, insert, delete)
     */
    public static int executeUpdate(String sql, Object ... params){
        int rows = 0;
        try {
            Connection conn = getConnection();
            rows = QUERY_RUNNER.update(conn,sql,params);
        } catch (SQLException e) {
            LOGGER.error("execute query failure", e);
            throw new RuntimeException(e);
        }


        return rows;
    }

    /**
     * 插入实体
     */
    public static <T> boolean insertEntity(Class<T> entityClass, Map<String,Object> fieldMap){
        if(CollectionUtil.isEmpty(fieldMap)){
            LOGGER.error("can not insert entity: fieldMap is empty");
            return false;
        }
        String sql = "INSERT INTO " + getTableName(entityClass);
        StringBuilder columns = new StringBuilder(" (");
        StringBuilder values = new StringBuilder(" (");
        for (String fieldName : fieldMap.keySet()){
            columns.append(fieldName).append(", ");
            values.append("?, ");
        }
        columns.replace(columns.lastIndexOf(", "),columns.length(),")");
        values.replace(values.lastIndexOf(", "),values.length(),")");
        sql += columns + " VALUES " + values;

        Object[] params = fieldMap.values().toArray();
        return executeUpdate(sql, params) ==1;
    }

    /**
     * 更新实体
     */
    public static <T> boolean updateEntity(Class<T> entityClass, long id, Map<String, Object> fieldMap){
         if(CollectionUtil.isEmpty(fieldMap)){
             LOGGER.error("cnat not update entity: fieldMap is empy");
             return false;
         }

        String sql = "UPDATE " + getTableName(entityClass) + " SET ";
        StringBuilder columns = new StringBuilder();
        for (String fieldName : fieldMap.keySet()){
            columns.append(fieldName).append("=?, ");
        }
        sql += columns.substring(0, columns.lastIndexOf(", ")) + " WHERE id = ?";

        List<Object> paramList = new ArrayList<Object>();
        paramList.addAll(fieldMap.values());
        paramList.add(id);
        Object[] params = paramList.toArray();

        return executeUpdate(sql, params) == 1;
    }

    /**
     * 删除实体
     */
    public static <T> boolean deleteEntity(Class<T> entityClass, long id){
        String sql = "DELETE FROM " + getTableName(entityClass) + " WHERE id = ?";
        return executeUpdate(sql, id) == 1;
    }

    /**
     * 执行sql文件
     */
    public static void executeSqlFile(String filePath){
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(filePath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        try {
            String sql;
            while ((sql = reader.readLine()) != null){
                executeUpdate(sql);
            }
        } catch (IOException e) {
            LOGGER.error("execute sql file failure", e);
            throw new RuntimeException(e);
        }
    }

    /**
     *
     * @param entityClass
     * @return
     */
    private static String getTableName(Class<?> entityClass) {
        return entityClass.getSimpleName();
    }

	/**
	 * 开启事务
	 */
	public static void  beginTransaction(){
		Connection conn = getConnection();
		if (conn != null){
			try {
				conn.setAutoCommit(false);
			} catch (SQLException e) {
				LOGGER.error("begin tansaction failure",e);
				throw new RuntimeException(e);
			} finally {
				CONNECTION_HOLDER.set(conn);
			}
		}
	}

	/**
	 * 提交事务
	 */
    public static void commitTransaction(){
		Connection conn = getConnection();
		if (conn != null){
			try {
				conn.commit();
				conn.close();
			} catch (SQLException e) {
				LOGGER.error("commite transaction failure",e);
				throw new RuntimeException(e);
			} finally {
				CONNECTION_HOLDER.remove();
			}
		}
	}

	/**
	 * 回滚事务
	 */
	public static void rollbackTransaction(){
		Connection conn = getConnection();
		if (conn != null){
			try {
				conn.rollback();
				conn.close();
			} catch (SQLException e) {
				LOGGER.error("rollback transaction failure", e);
			} finally {
				CONNECTION_HOLDER.remove();
			}
		}
	}
}
