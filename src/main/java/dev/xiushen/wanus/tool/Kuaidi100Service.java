package dev.xiushen.wanus.tool;

import com.google.gson.Gson;
import com.kuaidi100.sdk.api.AutoNum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Kuaidi100Service {
    private static final Logger LOGGER = LoggerFactory.getLogger(Kuaidi100Service.class);

    private final Gson gson = new Gson();

    private final AutoNum autoNum = new AutoNum();

//    @Tool(name = "logisticsInfo",
//            description = "Get the logistics information according to the given courier tracking number")
//    public ToolExecuteResult logisticsInfo(
//            @ToolParam(description = "tracking number") String num) {
//        QueryTrackResp queryTrackResp;
//        try {
//            queryTrackResp = queryTrack(num);
//            LOGGER.debug("queryTrackResp: {}", queryTrackResp);
//        }
//        catch (Exception e) {
//            LOGGER.error("Error occurred while querying track!", e);
//            throw new RuntimeException("Error querying track.", e);
//        }
//        return new ToolExecuteResult(new Gson().toJson(queryTrackResp));
//    }
//
//    private QueryTrackResp queryTrack(String num) throws Exception {
//        String key = kuaidi100Properties.getKey();
//        String customer = kuaidi100Properties.getCustomer();
//
//        QueryTrackParam queryTrackParam = createQueryTrackParam(num, key);
//        String param = gson.toJson(queryTrackParam);
//
//        QueryTrackReq queryTrackReq = createQueryTrackReq(customer, param, key);
//        return new QueryTrack().queryTrack(queryTrackReq);
//    }
//
//    private QueryTrackParam createQueryTrackParam(String num, String key) throws Exception {
//        AutoNumReq autoNumReq = new AutoNumReq();
//        autoNumReq.setNum(num);
//        autoNumReq.setKey(key);
//        String company = autoNum.getFirstComByNum(autoNumReq);
//
//        QueryTrackParam queryTrackParam = new QueryTrackParam();
//        queryTrackParam.setCom(company);
//        queryTrackParam.setNum(num);
//        return queryTrackParam;
//    }
//
//    private QueryTrackReq createQueryTrackReq(String customer, String param, String key) {
//        QueryTrackReq queryTrackReq = new QueryTrackReq();
//        queryTrackReq.setParam(param);
//        queryTrackReq.setCustomer(customer);
//        queryTrackReq.setSign(SignUtils.querySign(param, key, customer));
//        return queryTrackReq;
//    }
}
