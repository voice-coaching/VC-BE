package org.example.voice.common.logging;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration(proxyBeanMethods=false)
public class HttpLoggingConfiguration {
    @Bean public HttpLogRouteResolver httpLogRouteResolver(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings){return new HttpLogRouteResolver(mappings);}
    @Bean public FilterRegistrationBean<HttpRequestLoggingFilter> httpRequestLoggingFilter(HttpLogRouteResolver routes) {
        var bean=new FilterRegistrationBean<>(new HttpRequestLoggingFilter(routes));
        bean.setName("httpRequestLogging");bean.setOrder(Ordered.HIGHEST_PRECEDENCE+10);
        bean.setAsyncSupported(true);bean.setDispatcherTypes(DispatcherType.REQUEST,DispatcherType.ASYNC,DispatcherType.ERROR);
        bean.addUrlPatterns("/*");return bean;
    }
}
