package dev.xiushen.manus4j.tool.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties(Kuaidi100Properties.class)
@ConfigurationProperties(prefix = "spring.ai.alibaba.toolcalling.kuaidi100")
public class Kuaidi100Properties {
    /**
     * 授权key <a href="https://api.kuaidi100.com/manager/v2/myinfo/enterprise">获取授权key</a>
     */
    private String key;

    /**
     * customer
     * <a href="https://api.kuaidi100.com/manager/v2/myinfo/enterprise">获取customer</a>
     */
    private String customer;

    public Kuaidi100Properties(String key, String customer) {
        this.key = key;
        this.customer = customer;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
    }

    public String getKey() {
        return key;
    }

    public String getCustomer() {
        return customer;
    }
}
