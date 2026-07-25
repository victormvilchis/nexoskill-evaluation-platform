package com.nexoskill.evaluation.authentication.infrastructure.security;

import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OriginProtectionFilter extends OncePerRequestFilter {

	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

	private final AppProperties properties;

	public OriginProtectionFilter(AppProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		if (SAFE_METHODS.contains(request.getMethod())) {
			filterChain.doFilter(request, response);
			return;
		}

		String origin = request.getHeader("Origin");
		String fetchSite = request.getHeader("Sec-Fetch-Site");

		boolean originAllowed = origin == null || properties.getSecurity().getAllowedOrigins().contains(origin);
		boolean crossSite = "cross-site".equalsIgnoreCase(fetchSite);

		if (!originAllowed || crossSite) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.getWriter()
					.write("{\"code\":\"ORIGIN_REJECTED\"," + "\"message\":\"Origen de solicitud no permitido.\"}");
			return;
		}

		filterChain.doFilter(request, response);
	}
}
