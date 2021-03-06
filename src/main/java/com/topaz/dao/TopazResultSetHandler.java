package com.topaz.dao;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.lang.StringUtils;

import com.topaz.common.TopazUtil;

public class TopazResultSetHandler<T> implements ResultSetHandler<List<T>> {

	private Class<T> modelClass;

	public TopazResultSetHandler(Class<T> clazz) {
		this.modelClass = clazz;
	}

	public List<T> handle(ResultSet rs) throws SQLException {
		List<T> results = new ArrayList<T>();
		ResultSetMetaData rsmd = rs.getMetaData();
		List<String> cNames = columnNames(rsmd);
		while (rs.next()) {
			T bean = this.newInstance(modelClass);
			int c = cNames.size();
			for (int i = 0; i < c; i++) {
				String cName = cNames.get(i);
				processColumn(rs, bean, cName, i + 1);
			}
			results.add(bean);
		}
		return results;
	}

	private List<String> columnNames(ResultSetMetaData rsmd) throws SQLException {

		int c = rsmd.getColumnCount();
		List<String> cNames = new ArrayList<String>(c);
		for (int i = 0; i < c; i++) {
			int cIndex = i + 1;
			String cName = rsmd.getColumnLabel(cIndex);
			if (StringUtils.isBlank(cName)) {
				cName = rsmd.getColumnName(cIndex);
			}
			cNames.add(cName);
		}
		return cNames;
	}

	protected <A> A newInstance(Class<A> c) throws SQLException {
		try {
			return c.newInstance();

		} catch (Exception e) {
			throw new DaoException(
					"Cannot create " + c.getName() + ": " + e.getMessage());
		}
	}

	private void processColumn(ResultSet rs, Object bean, String cName, int pos) throws SQLException {
		Map<String, PropMapping> props = BaseModel.MODEL_PROPS.get(modelClass);
		if (cName.indexOf('.') >= 0) {
			// Its a model property
			String[] arr = cName.split("\\.");
			String tblProp = TopazUtil.flat2camel(arr[0]);
			if (props.containsKey(tblProp)) {
				PropMapping pm = props.get(tblProp);
				Method method = pm.getReadMethod();
				Object subObj = null;
				try {
					subObj = method.invoke(bean);
					if (subObj == null) {
						subObj = this.newInstance(pm.getTargetType());
						this.callSetter(bean, pm, subObj);
					}
				} catch (Exception e) {
					throw new DaoException(e);
				}

				// Find the sub object
				bean = subObj;
				cName = arr[1];
				props = BaseModel.MODEL_PROPS.get(pm.getTargetType());
			}
		}
		
		String pName = TopazUtil.flat2camel(cName);
		if (props.containsKey(pName)) {
			PropMapping pm = props.get(pName);
			Object cValue = processColumnValue(rs, pos, pm.getTargetType());
			callSetter(bean, pm, cValue);
		}
	}

	/**
	 * Convert a <code>ResultSet</code> column into an object. Simple
	 * implementations could just call <code>rs.getObject(index)</code> while
	 * more complex implementations could perform type manipulation to match the
	 * column's type to the bean property type.
	 * 
	 * <p>
	 * This implementation calls the appropriate <code>ResultSet</code> getter
	 * method for the given property type to perform the type conversion. If the
	 * property type doesn't match one of the supported <code>ResultSet</code>
	 * types, <code>getObject</code> is called.
	 * </p>
	 * 
	 * @param rs
	 *            The <code>ResultSet</code> currently being processed. It is
	 *            positioned on a valid row before being passed into this
	 *            method.
	 * 
	 * @param index
	 *            The current column index being processed.
	 * 
	 * @param propType
	 *            The bean property type that this column needs to be converted
	 *            into.
	 * 
	 * @throws SQLException
	 *             if a database access error occurs
	 * 
	 * @return The object from the <code>ResultSet</code> at the given column
	 *         index after optional type processing or <code>null</code> if the
	 *         column value was SQL NULL.
	 */
	protected Object processColumnValue(ResultSet rs, int index, Class<?> propType)
			throws SQLException {

		if (!propType.isPrimitive() && rs.getObject(index) == null) {
			return null;
		}

		if (propType.equals(String.class)) {
			return rs.getString(index);

		} else if (propType.equals(Integer.TYPE) || propType.equals(Integer.class)) {
			return Integer.valueOf(rs.getInt(index));

		} else if (propType.equals(Boolean.TYPE) || propType.equals(Boolean.class)) {
			return Boolean.valueOf(rs.getBoolean(index));

		} else if (propType.equals(Long.TYPE) || propType.equals(Long.class)) {
			return Long.valueOf(rs.getLong(index));

		} else if (propType.equals(Double.TYPE) || propType.equals(Double.class)) {
			return Double.valueOf(rs.getDouble(index));

		} else if (propType.equals(Float.TYPE) || propType.equals(Float.class)) {
			return Float.valueOf(rs.getFloat(index));

		} else if (propType.equals(Short.TYPE) || propType.equals(Short.class)) {
			return Short.valueOf(rs.getShort(index));

		} else if (propType.equals(Byte.TYPE) || propType.equals(Byte.class)) {
			return Byte.valueOf(rs.getByte(index));

		} else if (propType.equals(Timestamp.class)) {
			return rs.getTimestamp(index);

		} else if (propType.equals(SQLXML.class)) {
			return rs.getSQLXML(index);

		} else {
			return rs.getObject(index);
		}

	}

	/**
	 * Calls the setter method on the target object for the given property. If
	 * no setter method exists for the property, this method does nothing.
	 * 
	 * @param target
	 *            The object to set the property on.
	 * @param prop
	 *            The property to set.
	 * @param value
	 *            The value to pass into the setter.
	 * @throws SQLException
	 *             if an error occurs setting the property.
	 */
	private void callSetter(Object target, PropMapping pm, Object value)
			throws SQLException {

		Method setter = pm.getWriteMethod();

		if (setter == null) {
			return;
		}

		Class<?>[] params = setter.getParameterTypes();
		try {
			// convert types for some popular ones
			if (value instanceof java.util.Date) {
				final String targetType = params[0].getName();
				if ("java.sql.Date".equals(targetType)) {
					value = new java.sql.Date(((java.util.Date) value).getTime());
				} else if ("java.sql.Time".equals(targetType)) {
					value = new java.sql.Time(((java.util.Date) value).getTime());
				} else if ("java.sql.Timestamp".equals(targetType)) {
					value = new java.sql.Timestamp(((java.util.Date) value).getTime());
				}
			}

			// Don't call setter if the value object isn't the right type
			if (this.isCompatibleType(value, params[0])) {
				setter.invoke(target, new Object[] { value });
			} else {
				throw new SQLException(
						"Cannot set " + pm.getPropertyName()
								+ ": incompatible types, cannot convert "
								+ value.getClass().getName() + " to " + params[0].getName());
				// value cannot be null here because isCompatibleType allows
				// null
			}

		} catch (IllegalArgumentException e) {
			throw new SQLException(
					"Cannot set " + pm.getPropertyName() + ": " + e.getMessage());

		} catch (IllegalAccessException e) {
			throw new SQLException(
					"Cannot set " + pm.getPropertyName() + ": " + e.getMessage());

		} catch (InvocationTargetException e) {
			throw new SQLException(
					"Cannot set " + pm.getPropertyName() + ": " + e.getMessage());
		}
	}

	/**
	 * ResultSet.getObject() returns an Integer object for an INT column. The
	 * setter method for the property might take an Integer or a primitive int.
	 * This method returns true if the value can be successfully passed into the
	 * setter method. Remember, Method.invoke() handles the unwrapping of
	 * Integer into an int.
	 * 
	 * @param value
	 *            The value to be passed into the setter method.
	 * @param type
	 *            The setter's parameter type (non-null)
	 * @return boolean True if the value is compatible (null => true)
	 */
	private boolean isCompatibleType(Object value, Class<?> type) {
		// Do object check first, then primitives
		if (value == null || type.isInstance(value)) {
			return true;

		} else if (type.equals(Integer.TYPE) && Integer.class.isInstance(value)) {
			return true;

		} else if (type.equals(Long.TYPE) && Long.class.isInstance(value)) {
			return true;

		} else if (type.equals(Double.TYPE) && Double.class.isInstance(value)) {
			return true;

		} else if (type.equals(Float.TYPE) && Float.class.isInstance(value)) {
			return true;

		} else if (type.equals(Short.TYPE) && Short.class.isInstance(value)) {
			return true;

		} else if (type.equals(Byte.TYPE) && Byte.class.isInstance(value)) {
			return true;

		} else if (type.equals(Character.TYPE) && Character.class.isInstance(value)) {
			return true;

		} else if (type.equals(Boolean.TYPE) && Boolean.class.isInstance(value)) {
			return true;

		}
		return false;

	}

}
