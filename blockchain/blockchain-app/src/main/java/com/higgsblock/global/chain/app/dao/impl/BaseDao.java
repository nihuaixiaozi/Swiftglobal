package com.higgsblock.global.chain.app.dao.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Common basic approach.
 *
 * @author yangshenghong
 * @date 2018-05-08
 */
public abstract class BaseDao<T> {

    @Autowired
    protected NamedParameterJdbcTemplate template;

    /**
     * A common way to add data.
     *
     * @param t   entity
     * @param sql The SQL statement
     * @return
     */
    public int add(T t, String sql) {
        return template.update(sql, new BeanPropertySqlParameterSource(t));
    }

    /**
     * A common way to update data.
     *
     * @param t   entity
     * @param sql The SQL statement
     * @return
     */
    public int update(T t, String sql) {
        return template.update(sql, new BeanPropertySqlParameterSource(t));
    }

    /**
     * A common way to delete data.
     *
     * @param t   entity
     * @param sql The SQL statement
     * @return
     */
    public int delete(T t, String sql) {
        return template.update(sql, new BeanPropertySqlParameterSource(t));
    }

    /**
     * Delete according to the specified field.
     *
     * @param sql      The SQL statement
     * @param paramMap The data Map
     * @return
     */
    public int delete(String sql, Map<String, ?> paramMap) {
        return template.update(sql, paramMap);
    }

    /**
     * Generic query data based on fields.
     *
     * @param t   entity
     * @param sql The SQL statement
     * @return
     */
    public T getByField(T t, String sql) {
        try {
            T t1 = (T) template.queryForObject(sql, new BeanPropertySqlParameterSource(t), new BeanPropertyRowMapper<>(getT()));
            return t1;
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    /**
     * Generic query data based on fields.
     *
     * @param sql      The SQL statement
     * @param paramMap Field data
     * @return
     */
    public T getByField(String sql, Map<String, ?> paramMap) {
        try {
            T t1 = (T) template.queryForObject(sql, paramMap, new BeanPropertyRowMapper<>(getT()));
            return t1;
        } catch (RuntimeException e) {
            //e.printStackTrace();
            System.err.println(e.getMessage());
            return null;
        }
    }

    /**
     * Generic query data based on fields.
     *
     * @param sql      The SQL statement
     * @param paramMap Field data
     * @return
     */
    public List<T> getByFieldList(String sql, Map<String, ?> paramMap) {
        try {
            return template.query(sql, paramMap, new BeanPropertyRowMapper<>(getT()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * get all data
     *
     * @param sql The SQL statement
     * @return
     */
    public List<T> findAll(String sql) {
        return template.query(sql, new BeanPropertyRowMapper<>(getT()));
    }

    /**
     * Paging queries all data.
     *
     * @param paramMap
     * @param sql      The SQL statement
     * @return
     */
    public List<T> findAll(Map<String, ?> paramMap, String sql) {
        return template.query(sql, paramMap, new BeanPropertyRowMapper<>(getT()));
    }

    private Class getT() {
        Class<? extends BaseDao> clazz = this.getClass();
        ParameterizedType type = (ParameterizedType) clazz.getGenericSuperclass();
        Type[] types = type.getActualTypeArguments();
        if (null != types) {
            return (Class) types[0];
        }
        return null;
    }
}
