package org.example.voice.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.http.server.PathContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Returns registered templates, including for requests rejected before MVC. Never returns a raw URL. */
public final class HttpLogRouteResolver {
    private final RequestMappingHandlerMapping mappings;
    private volatile List<Route> routes;
    public HttpLogRouteResolver(RequestMappingHandlerMapping mappings) {this.mappings=mappings;}
    public String resolve(HttpServletRequest request) {
        try {
            if(routes==null) initialize();
            var path=PathContainer.parsePath(request.getRequestURI());
            String method=request.getMethod();
            return routes.stream().filter(r -> (r.methods().isEmpty() || r.methods().contains(method)
                    || ("HEAD".equals(method) && r.methods().contains("GET")) || "OPTIONS".equals(method))
                    && r.pattern().matches(path))
                    .min((a,b)->PathPattern.SPECIFICITY_COMPARATOR.compare(a.pattern(),b.pattern()))
                    .map(r->r.pattern().getPatternString()).orElse("<unmapped>");
        } catch(RuntimeException ignored) {return "<unmapped>";}
    }
    private synchronized void initialize() {
        if(routes!=null)return;
        var result=new ArrayList<Route>();
        var parser=new PathPatternParser();
        mappings.getHandlerMethods().keySet().forEach(info->{
            var methods=info.getMethodsCondition().getMethods().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet());
            info.getPatternValues().forEach(path->result.add(new Route(parser.parse(path),methods)));
        });
        routes=List.copyOf(result);
    }
    private record Route(PathPattern pattern, Set<String> methods) {}
}
