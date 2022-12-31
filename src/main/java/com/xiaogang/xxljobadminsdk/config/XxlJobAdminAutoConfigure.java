package com.xiaogang.xxljobadminsdk.config;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.xiaogang.xxljobadminsdk.dto.HttpHeader;
import com.xiaogang.xxljobadminsdk.dto.ReturnT;
import com.xiaogang.xxljobadminsdk.service.XxlJobService;
import com.xiaogang.xxljobadminsdk.service.impl.XxlJobServiceImpl;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.HttpCookie;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(XxlJobAdminProperties.class)
public class XxlJobAdminAutoConfigure {

    private Logger logger = LoggerFactory.getLogger(XxlJobAdminAutoConfigure.class);

    @Bean
    public XxlJobService xxlJobService(HttpHeader loginHeader, XxlJobAdminProperties xxlJobAdminProperties) {
        logger.info(">>>>>>>>>>> xxl-job config init. xxlJobService");
        XxlJobService xxlJobService = new XxlJobServiceImpl(loginHeader, xxlJobAdminProperties);
        return xxlJobService;
    }

    @Bean("loginHeader")
    public HttpHeader httpRequest(XxlJobAdminProperties xxlJobAdminProperties){
        logger.info(">>>>>>>>>>> xxl-job config init. httpRequest");
        String adminUrl = xxlJobAdminProperties.getAdminUrl();
        String userName = xxlJobAdminProperties.getUserName();
        String password = xxlJobAdminProperties.getPassword();
        int connectionTimeOut = xxlJobAdminProperties.getConnectionTimeOut();

        Map<String, Object > paramMap = new HashMap<>();
        paramMap.put("userName",userName);
        paramMap.put("password",password);

        HttpResponse httpResponse = HttpRequest.post(adminUrl+"/login").form(paramMap).timeout(connectionTimeOut).execute();
        int status = httpResponse.getStatus();

        if (200 != status) {
            throw new RuntimeException("登录失败");
        }

        String body = httpResponse.body();
        ReturnT returnT = JSONUtil.toBean(body, ReturnT.class);
        if (200 != returnT.getCode()) {
            throw new RuntimeException("登录失败:"+returnT.getMsg());
        }

        String cookieName = "XXL_JOB_LOGIN_IDENTITY";
        HttpCookie cookie = httpResponse.getCookie(cookieName);
        if (cookie == null) {
            throw new RuntimeException("没有获取到登录成功的cookie，请检查登录连接或者参数是否正确");
        }
        String headerValue = new StringBuilder(cookieName).append("=").append(cookie.getValue()).toString();
        HttpHeader loginHeader = new HttpHeader("Cookie",headerValue);
        return loginHeader;
    }

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(XxlJobAdminProperties xxlJobAdminProperties) {
        logger.info(">>>>>>>>>>> xxl-job config init. XxlJobSpringExecutor");
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(xxlJobAdminProperties.getAdminUrl());
        xxlJobSpringExecutor.setAppname(xxlJobAdminProperties.getAppname());
        xxlJobSpringExecutor.setAddress(xxlJobAdminProperties.getAddress());
        xxlJobSpringExecutor.setIp(xxlJobAdminProperties.getIp());
        xxlJobSpringExecutor.setPort(xxlJobAdminProperties.getPort());
        xxlJobSpringExecutor.setAccessToken(xxlJobAdminProperties.getAccessToken());
        String logPath = xxlJobAdminProperties.getLogPath();
        if (logPath != null) {
            xxlJobSpringExecutor.setLogPath(logPath);
        }
        Integer logRetentionDays = xxlJobAdminProperties.getLogRetentionDays();
        if (logRetentionDays != null) {
            xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);
        }

        return xxlJobSpringExecutor;
    }
}
