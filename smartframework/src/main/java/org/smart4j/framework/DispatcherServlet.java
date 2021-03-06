package org.smart4j.framework;

import org.smart4j.framework.bean.Data;
import org.smart4j.framework.bean.Handler;
import org.smart4j.framework.bean.Param;
import org.smart4j.framework.bean.View;
import org.smart4j.framework.helper.*;
import org.smart4j.framework.util.JsonUtil;
import org.smart4j.framework.util.ReflectionUtil;
import org.smart4j.framework.util.StringUtil;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Created by AdrainHuang on 2017/1/30.
 */
@WebServlet(urlPatterns = "/*", loadOnStartup = 0)
public class DispatcherServlet extends HttpServlet {

	@Override
	public void init(ServletConfig servletConfig) throws ServletException {
		//初始化相关 Helper 类
		HelperLoader.init();
		//获取 ServletContext 对象（用于注解Servlet）
		ServletContext servletContext = servletConfig.getServletContext();
		//注册处理JSP 的 Servlet
		ServletRegistration jspServlet = servletContext.getServletRegistration("jsp");
		jspServlet.addMapping(ConfigHelper.getAppJspPath() + "*");
		//注册处理静态资源的默认 Servlet
		ServletRegistration defaultServlet = servletContext.getServletRegistration("default");
		defaultServlet.addMapping(ConfigHelper.getAppAssetPath() + "*");
		UploaderHelper.init(servletContext);
	}

	@Override
	public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		//获取请求方法与请求路径
		String requestMethod = request.getMethod().toLowerCase();
		String requestPath = request.getPathInfo();
		if (requestPath.equals("/favicon.ico")){
			return;
		}
		//获取 Action 处理器
		Handler handler = ControllerHelper.getHandler(requestMethod, requestPath);
		if (handler != null){
			//创建请求参数对象
//			Map<String , Object> paramMap = new HashMap<String, Object>();
//			Enumeration<String> paramNames = request.getParameterNames();
//			while (paramNames.hasMoreElements()){
//				String paramName = paramNames.nextElement();
//				String paramValue = request.getParameter(paramName);
//				paramMap.put(paramName, paramValue);
//			}
//			String body = CodecUtil.decodeURL(StreamUtil.getString(request.getInputStream()));
//			if (StringUtil.isNotEmpty(body)){
//				String[] parmas = StringUtil.splitString(body,"&");
//				if (ArrayUtil.isNotEmpty(parmas)){
//					for (String param : parmas){
//						String[] array = StringUtil.splitString(param, "=");
//						if (ArrayUtil.isNotEmpty(array) && array.length==2){
//							String paramName = array[0];
//							String paramValue = array[1];
//							paramMap.put(paramName, paramValue);
//						}
//					}
//				}
//			}
//			Param param = new Param(paramMap);
			//获取 Controller 类及其 Bean 实例
			Class<?> controllerClass = handler.getControllerClass();
			Object controllerBean = BeanHelper.getBean(controllerClass);
			Param param;
			if (UploaderHelper.isMultipart(request)){
				param = UploaderHelper.createParam(request);
			}else {
				param = RequestHelper.createParam(request);
			}
			//调用 Action方法
			Method acctionMethod = handler.getAcctionMethod();
			//TODO 为了框架能走下去所以 第三个参数 设置为Null值了。
			Object result;
			if (param.isEmpty()){
				result = ReflectionUtil.invokeMethod(controllerBean,acctionMethod);
			}else {
				result = ReflectionUtil.invokeMethod(controllerBean, acctionMethod, param);
			}
			//处理 Actoin 方法返回值
			if (result instanceof View){
				//返回JSP页面
				handleViewRequest(request, response, (View) result);
			} else if (request instanceof Data){
				//返回JSON数据
				handleDataResult(response, (Data) result);
			}
		}
	}

	private void handleDataResult(HttpServletResponse response, Data data) throws IOException {
		Object model = data.getModel();
		if (model != null){
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			PrintWriter writer = response.getWriter();
			String json = JsonUtil.toJson(model);
			writer.write(json);
			writer.flush();
			writer.close();
		}
	}

	private void handleViewRequest(HttpServletRequest request, HttpServletResponse response, View view) throws
			IOException, ServletException {
		String path = view.getPath();
		if (StringUtil.isNotEmpty(path)){
			if (path.startsWith("/")){
				response.sendRedirect(request.getContextPath() + path);
			} else {
				Map<String, Object> model = view.getModel();
				for (Map.Entry<String,Object> entry: model.entrySet()){
					request.setAttribute(entry.getKey(), entry.getValue());
				}
				request.getRequestDispatcher(ConfigHelper.getAppJspPath() + path).forward(request,response);
			}
		}
	}
}
