package com.tencent.trpcprotocol.qqhb.access.mobile_hb_cgi;

import com.tencent.mobileqq.pb.MessageMicro;
import com.tencent.mobileqq.pb.PBEnumField;
import com.tencent.mobileqq.pb.PBField;
import com.tencent.mobileqq.pb.PBInt64Field;
import com.tencent.mobileqq.pb.PBStringField;

public final class MobileHbCgiPB {

    public static final class Auth extends MessageMicro<Auth> {
        public final PBStringField authkey = PBField.initString("");

        public Auth() {
        }
    }

    public static final class HBPreGrabReq extends MessageMicro<HBPreGrabReq> {
        public final PBStringField listid = PBField.initString("");
        public final PBEnumField group_type = PBField.initEnum(0);
        public final PBStringField groupid = PBField.initString("");
        public final PBStringField send_uin = PBField.initString("");
        public final Auth auth = new Auth();

        public HBPreGrabReq() {
        }
    }

    public static final class HBGrabDetail extends MessageMicro<HBGrabDetail> {
        public final PBInt64Field total_amount = PBField.initInt64(0);
        public final PBInt64Field total_num = PBField.initInt64(0);
        public final PBInt64Field recv_num = PBField.initInt64(0);

        public HBGrabDetail() {
        }
    }

    public static final class HBPreGrabRsp extends MessageMicro<HBPreGrabRsp> {
        public final PBEnumField state = PBField.initEnum(0);
        public final PBStringField pre_grap_token = PBField.initString("");
        public final HBGrabDetail send_object = new HBGrabDetail();

        public HBPreGrabRsp() {
        }
    }
}
