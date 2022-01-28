package cn.yiidii.sb.cmd.cmd;

/**
 * 命令的BeanName常量
 *
 * @author ed w
 * @since 1.0
 */
public interface CmdBeanName {

    String LT_HELP = "lt_Help";
    String LT_SEND_SMS = "lt_sendSms";
    String LT_LOGIN = "lt_login";
    String LT_SET_THRESHOLD = "lt_setThreshold";
    String LT_START_MONITOR = "lt_startMonitor";
    String LT_STOP_MONITOR = "lt_stopMonitor";
    String LT_RESET_OFFSET = "lt_resetOffset";
    String LT_UNBIND_PHONE = "lt_unbindPhone";
    String LT_REMOVE_ALL_PHONE = "lt_removeAllPhone";
    String LT_FLOW_STATISTIC = "lt_flowStatistic";
    String LT_CAT_BOUND_PHONE = "lt_catBoundPhone";

    String ZHISI_HELP = "zhisi_help";
    String ZHISI_AI_BEGIN = "zhisi_aiBegin";
    String ZHISI_AI_END = "zhisi_aiEnd";

}
