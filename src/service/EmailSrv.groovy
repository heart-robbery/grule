package service

import cn.xnatural.app.ServerTpl
import cn.xnatural.enet.event.EL

class EmailSrv extends ServerTpl {

    @Lazy String host = getStr('host', 'smtp.qq.com')
    @Lazy String sender = getStr('sender', 'xnatural@msn.cn')
    @Lazy String password = getStr('password', null)


    @EL(name = 'sys.starting', async = true)
    def start() {

    }


    def email(@DelegatesTo(EmailSpec) Closure cl) {
        def email = new EmailSpec()
        def code = cl.rehydrate(email, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        code()
        send(email)
    }


    protected send(EmailSpec spec) {

    }


    class EmailSpec {
        String from
        List<String> to
        String subject
        String body
    }
}
