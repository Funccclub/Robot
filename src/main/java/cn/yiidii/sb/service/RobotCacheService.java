package cn.yiidii.sb.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.yiidii.pigeon.common.core.base.enumeration.Status;
import cn.yiidii.pigeon.common.core.util.SpringContextHolder;
import cn.yiidii.sb.cmd.cmd.impl.LtCommand;
import cn.yiidii.sb.common.constant.SbScheduleNameConstant;
import cn.yiidii.sb.common.prop.SbSystemProperties;
import cn.yiidii.sb.common.prop.SbSystemProperties.RobotConfig;
import cn.yiidii.sb.model.bo.LtPhoneCache;
import cn.yiidii.sb.model.bo.QqCache;
import cn.yiidii.sb.model.bo.RobotCache;
import cn.yiidii.sb.model.dto.LtAccountInfo;
import cn.yiidii.sb.model.dto.LtAccountInfo.Resource;
import cn.yiidii.sb.util.ScheduleTaskUtil;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lz1998.pbbot.bot.Bot;
import net.lz1998.pbbot.bot.BotContainer;
import onebot.OnebotApi.GetFriendListResp;
import onebot.OnebotApi.GetFriendListResp.Friend;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * SingleQqCache service
 *
 * @author ed w
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RobotCacheService {

    private static final String DATA_PATH = System.getProperty("user.dir") + File.separator + "data" + File.separator + "data.json";
    private static final Map<Long, RobotCache> DATA_CACHE = new ConcurrentHashMap<>();
    private static final List<String> FILTERED_LT_RESOURCE_TYPE = Lists.newArrayList("I2");
    private static final BigDecimal NUM_1024 = new BigDecimal("1024");

    private static boolean INIT = false;

    private final ChinaUnicomService chinaUnicomService;
    private final BotContainer botContainer;
    private final SbSystemProperties systemProperties;
    private final ScheduleTaskUtil scheduleTaskUtil;

    @PostConstruct
    public void init() {
        try {
            String s = FileUtil.readString(DATA_PATH, StandardCharsets.UTF_8);
            JSONObject cacheJo = JSONUtil.toBean(JSONUtil.parseObj(s), JSONObject.class);
            cacheJo.forEach((k, v) -> DATA_CACHE.put(Long.valueOf(k), BeanUtil.toBean(JSONObject.parseObject(v.toString()), RobotCache.class)));
        } catch (IORuntimeException e) {
            FileUtil.writeString("{}", DATA_PATH, StandardCharsets.UTF_8);
        }
        INIT = true;
    }

    public void addOrUpdateRobot(Long robotQq) {
        Map<Long, RobotConfig> configuredRobotMap = systemProperties.getRobot().stream().collect(Collectors.toMap(RobotConfig::getQq, e -> e));
        RobotConfig robotConfig = configuredRobotMap.get(robotQq);
        if (!configuredRobotMap.containsKey(robotQq)) {
            return;
        }
        if (DATA_CACHE.containsKey(robotConfig.getQq())) {
            DATA_CACHE.get(robotQq).setName(robotConfig.getName());
        } else {
            DATA_CACHE.put(robotQq, new RobotCache(robotQq, robotConfig.getName()));
        }
    }

    public Bot getBot(Long robotId) {
        Map<Long, RobotConfig> configuredRobots = systemProperties.getRobot().stream().collect(Collectors.toMap(RobotConfig::getQq, e -> e, (e1, e2) -> e2));
        if (!configuredRobots.containsKey(robotId)) {
            throw new RuntimeException(StrUtil.format("??????????????????(QQ: {})", robotId));
        }
        Map<Long, Bot> bots = botContainer.getBots();
        Bot bot = bots.get(robotId);
        if (Objects.isNull(bot) || !bot.getBotSession().isOpen()) {
            throw new RuntimeException(StrUtil.format("?????????(QQ: {})?????????", robotId));
        }
        return bot;
    }

    public RobotConfig getRobotConfig(Long robotId) {
        Map<Long, RobotConfig> configuredRobots = systemProperties.getRobot().stream().collect(Collectors.toMap(RobotConfig::getQq, e -> e, (e1, e2) -> e2));
        if (!configuredRobots.containsKey(robotId)) {
            throw new RuntimeException(StrUtil.format("??????????????????(QQ: {})", robotId));
        }
        Map<Long, Bot> bots = botContainer.getBots();
        Bot bot = bots.get(robotId);
        if (Objects.isNull(bot) || !bot.getBotSession().isOpen()) {
            throw new RuntimeException(StrUtil.format("?????????(QQ: {})?????????", robotId));
        }
        return configuredRobots.get(robotId);
    }

    public Map<Long, Friend> getFriendMap(Long robotId) {
        GetFriendListResp friendList = this.getBot(robotId).getFriendList();
        return friendList.getFriendList().stream().collect(Collectors.toMap(Friend::getUserId, e -> e, (e1, e2) -> e2));
    }

    public Friend getFriend(Long robotId, Long friendId) {
        return this.getFriendMap(robotId).get(friendId);
    }

    public void sendPrivateMessage(Long robotId, Long friendId, String message) {
        if (StrUtil.isBlank(message)) {
            throw new RuntimeException("????????????????????????");
        }
        this.getBot(robotId).sendPrivateMsg(friendId, message, true);
    }


    public void addOrUpdateQqInfo(Long robotQq, Long friendQq) {
        if (Objects.isNull(friendQq)) {
            return;
        }
        // ??????robot??????
        if (!DATA_CACHE.containsKey(robotQq)) {
            return;
        }
        // ???robot??????|????????????QQ
        Friend friend = this.getFriend(robotQq, friendQq);
        long qq = friend.getUserId();
        Map<Long, QqCache> qqCacheMap = DATA_CACHE.get(robotQq).getQqCacheMap();
        QqCache qqCache = qqCacheMap.get(qq);
        if (Objects.isNull(qqCache)) {
            qqCache = new QqCache(robotQq, qq, friend.getNickname(), friend.getRemark());
            qqCacheMap.put(qq, qqCache);
        } else {
            qqCache.setRobotQq(robotQq);
            qqCache.setQq(qq);
            qqCache.setNick(friend.getNickname());
            qqCache.setRemark(friend.getRemark());
        }
    }

    public void addOrUpdateLtPhone(Long robotQq, Long sender, LtPhoneCache ltPhone) {
        QqCache qqCache = this.getQq(robotQq, sender);
        Map<String, LtPhoneCache> ltPhoneCacheMap = qqCache.getLtPhoneCache();
        ltPhoneCacheMap.put(ltPhone.getPhone(), ltPhone);
        // ??????????????????
        this.monitor(qqCache, true);
    }

    public void setupLtPhoneThreshold(Long robotQq, Long sender, String phone, Long threshold) {
        QqCache qqCache = this.getQq(robotQq, sender);
        LtPhoneCache ltPhoneCache = qqCache.getLtPhoneCache().get(phone);
        if (Objects.isNull(ltPhoneCache)) {
            throw new RuntimeException(StrUtil.format("??????????????????{}", DesensitizedUtil.mobilePhone(phone)));
        }
        ltPhoneCache.setThreshold(threshold);
    }

    public void switchMonitorStatus(Long robotQq, Long sender, String phone, boolean monitor) {
        QqCache qqCache = this.getQq(robotQq, sender);
        LtPhoneCache ltPhoneCache = qqCache.getLtPhoneCache().get(phone);
        if (Objects.isNull(ltPhoneCache)) {
            throw new RuntimeException(StrUtil.format("??????????????????{}", DesensitizedUtil.mobilePhone(phone)));
        }
        ltPhoneCache.setMonitor(monitor);
    }

    public String getFlowStatistic(Long robotQq, Long sender, String phone) {
        QqCache qqCache = this.getQq(robotQq, sender);
        LtPhoneCache ltPhoneCache = qqCache.getLtPhoneCache().get(phone);
        if (Objects.isNull(ltPhoneCache)) {
            throw new RuntimeException(StrUtil.format("??????????????????{}", DesensitizedUtil.mobilePhone(phone)));
        }
        String msg = StrUtil.format("?????????{}\r\nCookie???{}\r\n?????????{}\r\n?????????{}MB\r\n???????????????{}\r\n???????????????{}\r\n???????????????{}\n???????????????{}\r\n?????????{}\r\n?????????{}\r\n?????????{}",
                DesensitizedUtil.mobilePhone(ltPhoneCache.getPhone()),
                ltPhoneCache.getStatus().equals(Status.ENABLED) ? "??????" : "?????????",
                ltPhoneCache.isMonitor() ? "?????????" : "?????????",
                ltPhoneCache.getThreshold(),
                formatFlow(ltPhoneCache.getSum()),
                formatFlow(ltPhoneCache.getUse()),
                formatFlow(ltPhoneCache.getFilteredUse()),
                formatFlow(ltPhoneCache.getFree()),
                formatFlow(ltPhoneCache.getOffset()),
                DateUtil.formatLocalDateTime(ltPhoneCache.getStatisticTime()),
                DateUtil.formatLocalDateTime(ltPhoneCache.getLastMonitorTime())
        );
        return msg;
    }

    public void resetOffset(Long robotQq, Long sender, String phone) {
        QqCache qqCache = this.getQq(robotQq, sender);
        LtPhoneCache ltPhoneCache = qqCache.getLtPhoneCache().get(phone);
        if (Objects.isNull(ltPhoneCache)) {
            throw new RuntimeException(StrUtil.format("??????????????????{}", DesensitizedUtil.mobilePhone(phone)));
        }
        ltPhoneCache.setOffset(BigDecimal.ZERO);
    }


    public List<String> getBoundPhone(Long robotQq, Long sender) {
        QqCache qqCache = this.getQq(robotQq, sender);
        Map<String, LtPhoneCache> ltPhoneCacheMap = qqCache.getLtPhoneCache();
        Assert.isTrue(CollUtil.isNotEmpty(ltPhoneCacheMap), StrUtil.format("???????????????????????????????????????????????????{}???", LtCommand.SEND_SMS.getDisplayName()));
        return ltPhoneCacheMap.keySet().stream().collect(Collectors.toList());
    }

    public List<String> unbindPhone(Long robotQq, Long sender, List<String> phones) {
        Assert.isTrue(CollUtil.isNotEmpty(phones), "?????????????????????????????????");

        QqCache qqCache = this.getQq(robotQq, sender);
        Map<String, LtPhoneCache> ltPhoneCacheMap = qqCache.getLtPhoneCache();

        Assert.isTrue(CollUtil.isNotEmpty(ltPhoneCacheMap), StrUtil.format("???????????????????????????????????????????????????{}???", LtCommand.SEND_SMS.getDisplayName()));

        List<String> successPhones = new ArrayList<>();
        phones.forEach(p -> {
            if (ltPhoneCacheMap.containsKey(p)) {
                successPhones.add(p);
                ltPhoneCacheMap.remove(p);
            }
        });
        return successPhones;
    }

    /**
     * ??????????????????
     */
    public void startTimerTask() {
        scheduleTaskUtil.startCron(SbScheduleNameConstant.ROBOT_TIMER_MONITOR, () -> timerMonitor(), systemProperties.getLtMonitorCron());
        scheduleTaskUtil.startCron(SbScheduleNameConstant.ROBOT_TIMER_PERSIST_CACHE_DATA, () -> timerPersistCacheData(), "0/20 * * * * ?");
    }

    /**
     * ?????????????????????
     */
    private void timerPersistCacheData() {
        if (!INIT) {
            return;
        }
        Thread.currentThread().setName(String.format(Thread.currentThread().getName(), SbScheduleNameConstant.ROBOT_TIMER_PERSIST_CACHE_DATA));
        String prettyJa = JSONUtil.toJsonPrettyStr(DATA_CACHE);
        FileUtil.writeString(prettyJa, DATA_PATH, StandardCharsets.UTF_8);
    }

    /**
     * ??????????????????
     */
    private void timerMonitor() {
        if (!INIT) {
            return;
        }
        Thread.currentThread().setName(String.format(Thread.currentThread().getName(), SbScheduleNameConstant.ROBOT_TIMER_MONITOR));
        ThreadPoolTaskExecutor executor = SpringContextHolder.getBean("asyncExecutor", ThreadPoolTaskExecutor.class);
        List<QqCache> qqCaches = DATA_CACHE.values().stream()
                .map(robotCache -> robotCache.getQqCacheMap().values())
                .flatMap(Collection::stream)
                .filter(qq -> CollUtil.isNotEmpty(qq.getLtPhoneCache()))
                .collect(Collectors.toList());
        if (qqCaches.size() > 0) {
            log.info("????????????????????????, ????????????{}?????????", qqCaches.size());
        }
        qqCaches.forEach(qq -> executor.execute(() -> this.monitor(qq, false)));
    }

    private QqCache getQq(Long robotQq, Long friendQq) {
        // ????????????friend, ??????friend?????????
        this.addOrUpdateQqInfo(robotQq, friendQq);
        // ?????????QQ??????????????????
        return DATA_CACHE.get(robotQq).getQqCacheMap().get(friendQq);
    }

    private void monitor(QqCache qqCache, boolean force) {
        Long robotQq = qqCache.getRobotQq();
        Long friendQq = qqCache.getQq();
        qqCache.getLtPhoneCache().values().forEach(ltPhoneCache -> {
            boolean canExecute = force || ltPhoneCache.isMonitor() || ltPhoneCache.getStatus().equals(Status.ENABLED);
            if (!canExecute) {
                return;
            }
            LtAccountInfo ltAccountInfo;
            try {
                ltAccountInfo = chinaUnicomService.getAccountInfo(ltPhoneCache.getCookie());
                ltPhoneCache.setStatus(Status.ENABLED);
            } catch (Exception e) {
                ltPhoneCache.setStatus(Status.DISABLED);
                return;
            }
            BigDecimal sum = ltAccountInfo.getSummary().getSum();
            BigDecimal free = ltAccountInfo.getSummary().getFreeFlow();
            BigDecimal use = sum.subtract(free);
            Optional<Resource> flowOptional = ltAccountInfo.getResources().
                    stream().filter(resource -> resource.getType().equals("flow"))
                    .findFirst();
            BigDecimal filteredUse = BigDecimal.ZERO;
            if (flowOptional.isPresent()) {
                filteredUse = flowOptional.get().getDetails().stream()
                        .filter(d -> !CollUtil.contains(FILTERED_LT_RESOURCE_TYPE, d.getResourceType()))
                        .map(d -> new BigDecimal(d.getUse()))
                        .reduce(BigDecimal.ZERO, (x, y) -> x.add(y));
            }

            // ??????-1?????????????????????????????????????????????????????????
            boolean firstTime = ltPhoneCache.getSum().compareTo(BigDecimal.ZERO) < 0;
            // ?????????????????????
            BigDecimal diff = filteredUse.subtract(ltPhoneCache.getUse());

            // ?????????
            ltPhoneCache.setSum(sum);
            ltPhoneCache.setFree(free);
            ltPhoneCache.setUse(use);
            ltPhoneCache.setFilteredUse(filteredUse);
            if (!firstTime) {
                // ??????????????????????????????
                ltPhoneCache.setOffset(ltPhoneCache.getOffset().add(diff));
            }
            ltPhoneCache.setLastMonitorTime(LocalDateTime.now());
            ltPhoneCache.setStatisticTime(DateUtil.parseLocalDateTime(ltAccountInfo.getTime()));
            boolean over = ltPhoneCache.getOffset().subtract(new BigDecimal(ltPhoneCache.getThreshold())).compareTo(BigDecimal.ZERO) > 0;
            // ??????
            if (over && !firstTime) {
                // ??????????????????
                String flowStatistic = this.getFlowStatistic(robotQq, friendQq, ltPhoneCache.getPhone());
                flowStatistic = "????????????\r\n".concat(flowStatistic).concat(StrUtil.format("\r\n\r\n\r\n???????????????{}???????????????, ?????????????????????????????????!", LtCommand.RESET_OFFSET.getDisplayName()));
                // ????????????
                this.sendPrivateMessage(robotQq, friendQq, flowStatistic);
                // ??????
                log.info(StrUtil.format("robotId, {}, QQ: {}, ?????????: {}, ????????????({}M/{}M), ???????????????", robotQq, friendQq, ltPhoneCache.getPhone(), ltPhoneCache.getOffset(), ltPhoneCache.getThreshold()));

            }
        });
    }

    /**
     * ???????????????
     *
     * @param num ??????????????????MB
     * @return
     */
    private String formatFlow(BigDecimal num) {
        if (num.subtract(NUM_1024).compareTo(BigDecimal.ZERO) > 0) {
            num = num.divide(NUM_1024, 2, RoundingMode.HALF_UP);
            if (num.subtract(NUM_1024).compareTo(BigDecimal.ZERO) > 0) {
                num = num.divide(NUM_1024, 2, RoundingMode.HALF_UP);
                return num + "TB";
            } else {
                return num + "GB";
            }
        } else {
            return num + "MB";
        }
    }
}
