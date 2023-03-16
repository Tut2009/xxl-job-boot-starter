package com.xiaogang.xxljobadminsdk.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.xiaogang.xxljobadminsdk.config.XxlJobAdminProperties;
import com.xiaogang.xxljobadminsdk.constants.*;
import com.xiaogang.xxljobadminsdk.dto.HttpHeader;
import com.xiaogang.xxljobadminsdk.dto.JobQuery;
import com.xiaogang.xxljobadminsdk.dto.ReturnT;
import com.xiaogang.xxljobadminsdk.model.DefaultXxlJobAddParam;
import com.xiaogang.xxljobadminsdk.model.XxlJobInfo;
import com.xiaogang.xxljobadminsdk.model.XxlJobInfoAddParam;
import com.xiaogang.xxljobadminsdk.service.XxlJobService;
import com.xiaogang.xxljobadminsdk.utils.MyUtil;
import com.xiaogang.xxljobadminsdk.vo.JobInfoPageItem;
import com.xiaogang.xxljobadminsdk.vo.JobInfoPageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class XxlJobServiceImpl implements XxlJobService {

    private HttpHeader loginHeader;

    private XxlJobAdminProperties xxlJobAdminProperties;

    private int timeout;

    public XxlJobServiceImpl(HttpHeader loginHeader, XxlJobAdminProperties xxlJobAdminProperties) {
        this.loginHeader = loginHeader;
        this.xxlJobAdminProperties = xxlJobAdminProperties;
        this.timeout = this.xxlJobAdminProperties.getConnectionTimeOut();
    }

    @Override
    public JobInfoPageResult pageList(JobQuery jobQuery) {
        this.validQueryParam(jobQuery);
        HttpRequest httpRequest = this.getHttpRequest(jobPageListPath);
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("start",jobQuery.getStart());
        paramMap.put("length",jobQuery.getLength());
        paramMap.put("jobGroup",jobQuery.getJobGroup());
        TriggerStatusEnum triggerStatus = jobQuery.getTriggerStatus();
        paramMap.put("triggerStatus", triggerStatus.getStatus());
        paramMap.put("jobDesc",jobQuery.getJobDesc());
        paramMap.put("executorHandler",jobQuery.getExecutorHandler());
        paramMap.put("author",jobQuery.getAuthor());

        HttpResponse response = httpRequest.form(paramMap).timeout(timeout).execute();
        int status = response.getStatus();
        String body = response.body();
        log.debug("status:{},body:{}",status,body);
        Assert.isTrue(status == 200,body);
        JobInfoPageResult jobInfoPageResult = JSON.parseObject(body, JobInfoPageResult.class);

        return jobInfoPageResult;
    }

    private void validQueryParam(JobQuery jobQuery){
        int jobGroup = jobQuery.getJobGroup();
        Assert.notNull(jobGroup,"jobGroup不能为null");
        TriggerStatusEnum triggerStatus = jobQuery.getTriggerStatus();
        Assert.notNull(triggerStatus,"triggerStatus不能为null");
        int start = jobQuery.getStart();
        Assert.notNull(start,"分页参数start不能为null");
        int length = jobQuery.getLength();
        Assert.notNull(length,"分页参数length不能为null");
    }

    @Override
    public Integer add(XxlJobInfo jobInfo) {
        HttpRequest httpRequest = postHttpRequest(jobAddPath);
        com.alibaba.fastjson.JSONObject jsonObject = JSON.parseObject(JSON.toJSONString(jobInfo));
        ReturnT<String> returnT = requestXxlJobAdmin(httpRequest, jsonObject, new TypeReference<ReturnT<String>>() {
        });
        return Integer.valueOf(returnT.getContent());
    }

    @Override
    public Integer add(XxlJobInfoAddParam addParam) {
        DefaultXxlJobAddParam defaultXxlJobAddParam = new DefaultXxlJobAddParam();
        BeanUtils.copyProperties(addParam,defaultXxlJobAddParam);
        Integer jobId = this.add(defaultXxlJobAddParam);
        return jobId;
    }

    @Override
    public Integer addJustExecuteOnceJob(String customId, Date triggerTime, String executorParam, String executorHandler) {
        JobInfoPageItem jobInfoPageItem = this.getJobByCustomId(customId);
        Assert.isNull(jobInfoPageItem,"已经存在自定义id为{}的任务，请修改",customId);
        Date now = new Date();
        boolean after = triggerTime.after(now);
        Assert.isTrue(after,"任务执行时间必须大于当前时间");
        XxlJobInfoAddParam addParam = new XxlJobInfoAddParam();
        Integer jobGroupId = xxlJobAdminProperties.getJobGroupId();
        addParam.setJobGroup(jobGroupId);
        addParam.setJobDesc("none");
        addParam.setAuthor(customId);
        addParam.setScheduleType(ScheduleTypeEnum.CRON);

        String cron = MyUtil.getCron(triggerTime);
        addParam.setScheduleConf(cron);
        addParam.setExecutorHandler(executorHandler);
        addParam.setExecutorParam(executorParam);
        Integer jobId = this.add(addParam);
        return jobId;
    }

    @Override
    public Integer getJobIdByCustomId(String customId) {
        JobInfoPageItem jobInfoPageItem = this.getJobByCustomId(customId);
        if (jobInfoPageItem == null) {
            return null;
        }
        return jobInfoPageItem.getId();
    }

    @Override
    public JobInfoPageItem getJobByCustomId(String customId) {
        JobQuery jobQuery = new JobQuery();
        jobQuery.setStart(0);
        jobQuery.setLength(1);
        jobQuery.setJobGroup(xxlJobAdminProperties.getJobGroupId());
        jobQuery.setTriggerStatus(TriggerStatusEnum.ALL);
        jobQuery.setAuthor(customId);
        JobInfoPageResult jobInfoPageResult = this.pageList(jobQuery);
        List<JobInfoPageItem> data = jobInfoPageResult.getData();
        if (CollUtil.isEmpty(data)) {
            return null;
        }
        JobInfoPageItem jobInfoPageItem = data.get(0);
        return jobInfoPageItem;
    }

    @Override
    public Integer add(DefaultXxlJobAddParam defaultXxlJobAddParam) {
        XxlJobInfo jobInfo = new XxlJobInfo();
        ScheduleTypeEnum scheduleType = defaultXxlJobAddParam.getScheduleType();
        if (scheduleType != null) {
            jobInfo.setScheduleType(scheduleType.name());
        }
        MisfireStrategyEnum misfireStrategy = defaultXxlJobAddParam.getMisfireStrategy();
        if (misfireStrategy != null) {
            jobInfo.setMisfireStrategy(misfireStrategy.name());
        }
        ExecutorRouteStrategyEnum executorRouteStrategy = defaultXxlJobAddParam.getExecutorRouteStrategy();
        if (executorRouteStrategy != null) {
            jobInfo.setExecutorRouteStrategy(executorRouteStrategy.name());
        }
        ExecutorBlockStrategyEnum executorBlockStrategy = defaultXxlJobAddParam.getExecutorBlockStrategy();
        if (executorBlockStrategy != null) {
            jobInfo.setExecutorBlockStrategy(executorBlockStrategy.name());
        }
        GlueTypeEnum glueType = defaultXxlJobAddParam.getGlueType();
        if (glueType != null) {
            jobInfo.setGlueType(glueType.name());
        }

        BeanUtils.copyProperties(defaultXxlJobAddParam,jobInfo);

        this.validAddJobParam(jobInfo);
        Integer jobId = this.add(jobInfo);
        return jobId;
    }

    private void validAddJobParam(XxlJobInfo jobInfo){
        Assert.notNull(jobInfo,"参数不能为null");
        Assert.notNull(jobInfo.getJobGroup(),"jobGroup参数不能为null");
        Assert.notNull(jobInfo.getJobDesc(),"jobDesc参数不能为null");
        Assert.notNull(jobInfo.getAuthor(),"author参数不能为null");
        Assert.notNull(jobInfo.getScheduleType(),"scheduleType参数不能为null");
        Assert.notNull(jobInfo.getGlueType(),"glueType参数不能为null");
        Assert.notNull(jobInfo.getExecutorHandler(),"executorHandler参数不能为null");
    }

    @Override
    public void update(XxlJobInfo jobInfo) {
        HttpRequest httpRequest = postHttpRequest(jobUpdatePath);

        com.alibaba.fastjson.JSONObject jsonObject = JSON.parseObject(JSON.toJSONString(jobInfo));
        requestXxlJobAdmin(httpRequest, jsonObject,new TypeReference<ReturnT<String>>(){});
    }

    @Override
    public void update(JobInfoPageItem jobInfoPageItem) {
        XxlJobInfo xxlJobInfo = this.transform(jobInfoPageItem);
        this.update(xxlJobInfo);
    }


    @Override
    public XxlJobInfo transform(JobInfoPageItem jobInfoPageItem){
        XxlJobInfo jobInfo = new XxlJobInfo();
        jobInfo.setId(jobInfoPageItem.getId());
        jobInfo.setJobGroup(jobInfoPageItem.getJobGroup());
        jobInfo.setJobDesc(jobInfoPageItem.getJobDesc());
        jobInfo.setAuthor(jobInfoPageItem.getAuthor());
        jobInfo.setAlarmEmail(jobInfoPageItem.getAlarmEmail());
        jobInfo.setScheduleType(jobInfoPageItem.getScheduleType());
        jobInfo.setScheduleConf(jobInfoPageItem.getScheduleConf());
        jobInfo.setMisfireStrategy(jobInfoPageItem.getMisfireStrategy());
        jobInfo.setExecutorRouteStrategy(jobInfoPageItem.getExecutorRouteStrategy());
        jobInfo.setExecutorHandler(jobInfoPageItem.getExecutorHandler());
        jobInfo.setExecutorParam(jobInfoPageItem.getExecutorParam());
        jobInfo.setExecutorBlockStrategy(jobInfoPageItem.getExecutorBlockStrategy());
        jobInfo.setExecutorTimeout(jobInfoPageItem.getExecutorTimeout());
        jobInfo.setExecutorFailRetryCount(jobInfoPageItem.getExecutorFailRetryCount());
        jobInfo.setGlueType(jobInfoPageItem.getGlueType());
        jobInfo.setGlueSource(jobInfoPageItem.getGlueSource());
        jobInfo.setGlueRemark(jobInfoPageItem.getGlueRemark());
        jobInfo.setChildJobId(jobInfoPageItem.getChildJobId());
        jobInfo.setTriggerLastTime(jobInfoPageItem.getTriggerLastTime());
        jobInfo.setTriggerNextTime(jobInfoPageItem.getTriggerNextTime());

        String addTime = jobInfoPageItem.getAddTime();
        if (StrUtil.isNotBlank(addTime)) {
            DateTime dateTime = DateUtil.parse(addTime);
            jobInfo.setAddTime(addTime);
        }
        String updateTime = jobInfoPageItem.getUpdateTime();
        if (StrUtil.isNotBlank(updateTime)) {
            DateTime dateTime = DateUtil.parse(updateTime);
            jobInfo.setUpdateTime(updateTime);
        }
        String glueUpdatetime = jobInfoPageItem.getGlueUpdatetime();
        if (StrUtil.isNotBlank(glueUpdatetime)) {
            DateTime dateTime = DateUtil.parse(glueUpdatetime);
            jobInfo.setGlueUpdatetime(glueUpdatetime);
        }
        Long triggerStatus = jobInfoPageItem.getTriggerStatus();
        if (triggerStatus != null) {
            jobInfo.setTriggerStatus(triggerStatus.intValue());
        }

        return jobInfo;
    }

    @Override
    public void remove(int id) {
        HttpRequest httpRequest = postHttpRequest(jobDeletePath);

        Map<String,Object> map = new HashMap<>();
        map.put("id",id);
        requestXxlJobAdmin(httpRequest, map,new TypeReference<ReturnT<String>>(){});
    }

    @Override
    public void remove(JobQuery jobQuery) {
        JobInfoPageResult jobInfoPageResult = this.pageList(jobQuery);
        List<JobInfoPageItem> data = jobInfoPageResult.getData();
        if (CollUtil.isEmpty(data)) {
            return;
        }

        for (JobInfoPageItem item : data) {
            this.remove(item.getId());
        }

        int i = 1;
        while (data.size() > 10) {
            data = jobInfoPageResult.getData();
            if (CollUtil.isEmpty(data)) {
                return;
            }
            for (JobInfoPageItem item : data) {
                this.remove(item.getId());
            }
            jobQuery.setStart(i++);
            jobInfoPageResult = this.pageList(jobQuery);
        }
    }

    @Override
    public void start(JobQuery jobQuery) {
        JobInfoPageResult jobInfoPageResult = this.pageList(jobQuery);
        List<JobInfoPageItem> data = jobInfoPageResult.getData();
        if (CollUtil.isEmpty(data)) {
            return;
        }

        for (JobInfoPageItem item : data) {
            this.start(item.getId());
        }

        int i = 1;
        while (data.size() > 10) {
            data = jobInfoPageResult.getData();
            if (CollUtil.isEmpty(data)) {
                return;
            }
            for (JobInfoPageItem item : data) {
                this.start(item.getId());
            }
            jobQuery.setStart(i++);
            jobInfoPageResult = this.pageList(jobQuery);
        }
    }

    @Override
    public void stopAndRemove(JobQuery jobGroup) {
        this.stop(jobGroup);
        this.remove(jobGroup);
    }

    @Override
    public void start(int id) {
        HttpRequest httpRequest = postHttpRequest(jobStartPath);

        Map<String,Object> map = new HashMap<>();
        map.put("id",id);
        requestXxlJobAdmin(httpRequest, map,new TypeReference<ReturnT<String>>(){});
    }

    @Override
    public void stop(int id) {
        HttpRequest httpRequest = postHttpRequest(jobStopPath);

        Map<String,Object> map = new HashMap<>();
        map.put("id",id);
        requestXxlJobAdmin(httpRequest, map,new TypeReference<ReturnT<String>>(){});
    }

    @Override
    public void stop(JobQuery jobQuery) {
        JobInfoPageResult jobInfoPageResult = this.pageList(jobQuery);
        List<JobInfoPageItem> data = jobInfoPageResult.getData();
        if (CollUtil.isEmpty(data)) {
            return;
        }

        for (JobInfoPageItem item : data) {
            this.stop(item.getId());
        }

        int i = 1;
        while (data.size() > 10) {
            data = jobInfoPageResult.getData();
            if (CollUtil.isEmpty(data)) {
                return;
            }
            for (JobInfoPageItem item : data) {
                this.stop(item.getId());
            }
            jobQuery.setStart(i++);
            jobInfoPageResult = this.pageList(jobQuery);
        }
    }

    @Override
    public void triggerJob(int id, String executorParam, String addressList) {
        HttpRequest httpRequest = postHttpRequest(jobTriggerPath);

        Map<String,Object> map = new HashMap<>();
        map.put("id",id);
        map.put("executorParam",executorParam);
        map.put("addressList",addressList);
        requestXxlJobAdmin(httpRequest, map,new TypeReference<ReturnT<String>>(){});
    }

    @Override
    public List<String> nextTriggerTime(String scheduleType, String scheduleConf) {
        HttpRequest httpRequest = getHttpRequest(jobNextTriggerTimePath);

        Map<String,Object> map = new HashMap<>();
        map.put("scheduleType",scheduleType);
        map.put("scheduleConf",scheduleConf);
        ReturnT<List<String>> returnT = requestXxlJobAdmin(httpRequest, map,new TypeReference<ReturnT<List<String>>>(){});
        return returnT.getContent();
    }

    private HttpRequest postHttpRequest(String path) {
        String url = new StringBuilder(xxlJobAdminProperties.getAdminUrl()).append(path).toString();
        HttpRequest httpRequest = HttpRequest.post(url);
        httpRequest.header(loginHeader.getHeaderName(),loginHeader.getHeaderValue());
        return httpRequest;
    }

    private HttpRequest getHttpRequest(String path) {
        String url = new StringBuilder(xxlJobAdminProperties.getAdminUrl()).append(path).toString();
        HttpRequest httpRequest = HttpRequest.get(url);
        httpRequest.header(loginHeader.getHeaderName(),loginHeader.getHeaderValue());
        return httpRequest;
    }

    private <T extends ReturnT> T requestXxlJobAdmin(HttpRequest httpRequest, Map<String, Object> paramMap,TypeReference<T> type) {
        HttpResponse response = httpRequest.form(paramMap).timeout(timeout).execute();
        int status = response.getStatus();
        String body = response.body();
        log.debug("status:{},body:{}",status,body);
        T returnT = JSON.parseObject(body, type);
        Assert.isTrue(status == 200, body);
        int code = returnT.getCode();
        log.debug("returnT:{}",returnT);
        Assert.isTrue(code == 200, returnT.getMsg());
        return returnT;
    }

}
