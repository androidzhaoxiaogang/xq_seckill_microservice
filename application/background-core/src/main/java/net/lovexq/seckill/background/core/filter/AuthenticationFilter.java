package net.lovexq.seckill.background.core.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureException;
import net.lovexq.seckill.background.core.properties.AppProperties;
import net.lovexq.seckill.background.core.repository.cache.ByteRedisClient;
import net.lovexq.seckill.background.core.support.security.JwtClaims;
import net.lovexq.seckill.background.core.support.security.JwtTokenUtil;
import net.lovexq.seckill.common.exception.ApplicationException;
import net.lovexq.seckill.common.utils.CookieUtil;
import net.lovexq.seckill.common.utils.constants.AppConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;

/**
 * 认证过滤器
 *
 * @author LuPindong
 * @time 2017-05-07 10:32
 */
@Order(1)
@WebFilter(filterName = "authenticationFilter", urlPatterns = "/specials/*")
public class AuthenticationFilter implements Filter {

    @Autowired
    private ByteRedisClient byteRedisClient;

    @Autowired
    private AppProperties appProperties;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String uri = request.getRequestURI();
        if (uri.contains("/specials/")) {

            Cookie tokenCookie = CookieUtil.getCookieByName(request, AppConstants.ACCESS_TOKEN);

            if (tokenCookie != null) {
                try {
                    // 请求的Token
                    String requestToken = tokenCookie.getValue();
                    Claims requestClaims = JwtTokenUtil.getClaims(requestToken, appProperties.getJwtSecretKey());
                    String requestAccount = requestClaims.getAudience();
                    String claimsUA = String.valueOf(requestClaims.get("userAgent"));
                    String claimsUN = String.valueOf(requestClaims.get("userName"));
                    String requestUA = request.getHeader("User-Agent").toLowerCase();

                    // 检查是否失效
                    if (JwtTokenUtil.isTokenExpired(requestClaims) || !claimsUA.equals(requestUA)) {
                        throw new ApplicationException("登录已失效，请重新登录！");
                    }

                    // 缓存的Token
                    String cacheKey = AppConstants.CACHE_ACCESS_TOKEN + requestAccount;
                    String redisToken = byteRedisClient.getByteObj(cacheKey, String.class);
                    if (redisToken == null || !requestToken.equals(redisToken)) {
                        throw new ApplicationException("Redis中无此Token！");
                    }

                    // 当前日期往前退5分钟，如果最后有效期在其中，则可以更新Token，实现自动续期
                    Date expiration = requestClaims.getExpiration();
                    long currentTime = System.currentTimeMillis();
                    if (expiration.after(new Date(currentTime - 600)) && expiration.before(new Date(currentTime))) {


                        // 重新生成Token
                        requestClaims = new JwtClaims(requestAccount, claimsUA, claimsUN);
                        // 延迟有效时间
                        String token = JwtTokenUtil.generateToken(requestClaims, appProperties.getJwtExpiration(), appProperties.getJwtSecretKey());

                        // 更新Cookie
                        CookieUtil.createCookie(AppConstants.ACCESS_TOKEN, token, "127.0.0.1", appProperties.getJwtExpiration(), true, response);
                        CookieUtil.createCookie(AppConstants.USER_NAME, claimsUN, "127.0.0.1", appProperties.getJwtExpiration(), response);

                        // 缓存Token
                        byteRedisClient.setByteObj(cacheKey, token, appProperties.getJwtExpiration());
                    }

                    request.setAttribute(AppConstants.CLAIMS, requestClaims);
                } catch (SignatureException e) {
                    throw new ApplicationException("非法请求，无效的Token！");
                } catch (JwtException e) {
                    throw new ApplicationException(e.getMessage(), e);
                } catch (ApplicationException e) {
                    throw new ApplicationException(e.getMessage(), e);
                }
                filterChain.doFilter(request, response);
            } else {
                throw new ApplicationException("受限内容，请登录后再操作！");
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {

    }
}
