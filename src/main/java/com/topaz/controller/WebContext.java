/**
 * Core component of Topaz framework, WebContext represent each reqeust resources and it is not thread safe.
 * 	- You can access request, response, session stuff here
 *  - You can get current controller name, current method and current Id.
 * 
 * @author foxty 
 */
package com.topaz.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;

import com.topaz.common.DataChecker;

public class WebContext {

	static public enum Accept {
		JSON, XML, HTML, JSONP;
	}

	public final static String FLASH = "flash";

	private HttpServletRequest request;
	private HttpServletResponse response;
	private HttpSession session;
	private ServletContext application;
	private Map<String, String> errors = new HashMap<String, String>();

	private String contextPath;
	private String moduleName;
	private String controllerName = "root";
	private String methodName = "index";
	private String viewBase;
	private boolean xssFilterOn = true;

	private static ThreadLocal<WebContext> local = new ThreadLocal<WebContext>();

	public static WebContext get() {
		return local.get();
	}

	public static WebContext create(HttpServletRequest req,
			HttpServletResponse resp, String viewBase) {
		WebContext ctx = new WebContext(req, resp, viewBase);
		local.set(ctx);
		return ctx;
	}

	private WebContext(HttpServletRequest req, HttpServletResponse resp,
			String viewBase) {

		this.request = req;
		this.response = resp;
		this.session = req.getSession();
		this.application = this.session.getServletContext();
		this.viewBase = StringUtils.isBlank(viewBase) ? "/view/" : (viewBase
				.endsWith("/") ? viewBase : viewBase + "/");
		this.contextPath = request.getContextPath();
	}

	public void xssFilterOn() {
		xssFilterOn = true;
	}

	public void xssFilterOff() {
		xssFilterOn = false;
	}

	public final ServletContext getApplication() {
		return application;
	}

	public final HttpServletRequest getRequest() {
		return request;
	}

	public final HttpServletResponse getResponse() {
		return response;
	}

	public final HttpSession getSession() {
		return session;
	}

	public Map<String, String> getErrors() {
		return errors;
	}

	public String getViewBase() {
		return viewBase;
	}

	public String getContextPath() {
		return contextPath;
	}

	public String getModuleName() {
		return moduleName;
	}

	public void setModuleName(String name) {
		moduleName = name;
	}

	public String getControllerName() {
		return controllerName;
	}

	public void setControllerName(String cName) {
		this.controllerName = cName;
	}

	public String getMethodName() {
		return methodName;
	}

	public void setMethodName(String mName) {
		this.methodName = mName;
	}

	public String getRequestResource() {
		return moduleName + "/" + controllerName + "/" + methodName;
	}

	public boolean isGet() {
		return this.request.getMethod().equalsIgnoreCase("GET");
	}

	public boolean isPost() {
		return this.request.getMethod().equalsIgnoreCase("POST");
	}

	public boolean isPUT() {
		return this.request.getMethod().equalsIgnoreCase("PUT");
	}

	public boolean isHEAD() {
		return this.request.getMethod().equalsIgnoreCase("HEAD");
	}

	public boolean isDELETE() {
		return this.request.getMethod().equalsIgnoreCase("DELETE");
	}

	public String header(String key) {
		return request.getHeader(key);
	}

	public void header(String k, String v) {
		response.setHeader(k, v);
	}

	/**
	 * 获取request的参数
	 * 
	 * @param key
	 * @return String
	 */
	public String param(String key) {
		String p = request.getParameter(key);
		if (xssFilterOn) {
			p = DataChecker.filterHTML(p);
		}
		return p;
	}

	/**
	 * 获取当前request的属性
	 * 
	 * @param key
	 * @return Object
	 */
	@SuppressWarnings("unchecked")
	public <T> T attr(String key) {
		Object attr = this.request.getAttribute(key);
		if (attr instanceof String && xssFilterOn) {
			attr = DataChecker.filterHTML((String) attr);
		}
		return (T) attr;
	}

	/**
	 * 设置对象至当前request中
	 * 
	 * @param key
	 * @param value
	 */
	public void attr(String key, Object value) {
		this.request.setAttribute(key, value);
	}

	/**
	 * 获取session中对象
	 * 
	 * @param key
	 * @return Object
	 */
	@SuppressWarnings("unchecked")
	public <T> T session(String key) {
		return (T) this.session.getAttribute(key);
	}

	/**
	 * 设置对象至当前session
	 * 
	 * @param key
	 * @param value
	 */
	public void session(String key, Object value) {
		this.session.setAttribute(key, value);
	}

	/**
	 * Get cookie object
	 * 
	 * @param name
	 * @return
	 */
	public String cookie(String name) {
		Cookie cookie = null;
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie c : cookies) {
				if (c.getName().equals(name)) {
					cookie = c;
				}
			}
		}
		return cookie != null ? cookie.getValue() : null;
	}

	/**
	 * Add cookie to response.
	 * 
	 * @param cookie
	 */
	public void cookie(String name, String value, String path, int maxAge,
			boolean httpOnly) {
		Cookie cookie = new Cookie(name, value);
		cookie.setPath(path);
		cookie.setMaxAge(maxAge);
		response.addCookie(cookie);
	}

	public void flash(String key, Object value) {
		ConcurrentHashMap<String, Object> flashMap = session(FLASH);
		if (flashMap == null) {
			flashMap = new ConcurrentHashMap<String, Object>();
			session(FLASH, flashMap);
		}
		flashMap.putIfAbsent(key, value);
	}

	@SuppressWarnings("unchecked")
	public <T> T flash(String key) {
		ConcurrentHashMap<String, Object> flashMap = session(FLASH);
		if (flashMap == null) {
			flashMap = new ConcurrentHashMap<String, Object>();
			session(FLASH, flashMap);
		}
		return (T) flashMap.get(key);
	}

	public void clearFlash() {
		ConcurrentHashMap<String, Object> flashMap = session(FLASH);
		if (flashMap != null)
			flashMap.clear();
	}

	public Accept getAccept() {
		Accept acc = Accept.HTML;
		String reqAccept = this.request.getHeader("Accept");
		if (reqAccept.contains("application/json"))
			acc = Accept.JSON;
		if (reqAccept.contains("application/xml"))
			acc = Accept.XML;
		return acc;
	}

	public boolean isAcceptJSON() {
		return getAccept() == Accept.JSON;
	}

	public boolean isAcceptXML() {
		return getAccept() == Accept.XML;
	}

	public boolean isAJAX() {
		return StringUtils.equals("XMLHttpRequest", header("X-Requested-With"));
	}

}
