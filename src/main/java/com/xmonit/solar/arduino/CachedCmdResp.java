package com.xmonit.solar.arduino;

public class CachedCmdResp {

    private String tty;
    private String cmd;
    private String resp;
    private long whenMs = 0;
    public long maxAgeMs = 30*60*1000;

    public CachedCmdResp(String cmd){
        this.cmd = cmd;
    }

    public synchronized void invalidate() {
        this.resp = null;
        this.tty = null;
        this.whenMs = 0;
    }

    public synchronized void update(String tty, String resp){
        this.tty = tty;
        this.resp = resp;
        this.whenMs = System.currentTimeMillis();
    }

    public synchronized String getLatest(String ttyRegEx) {
        boolean expired = System.currentTimeMillis() - whenMs > maxAgeMs;
        if ( expired ) {
            return null;
        }
        boolean ttyPassed = (ttyRegEx == null) || tty != null && tty.matches(ttyRegEx);
        if ( !ttyPassed ) {
            return null;
        }
        return resp;
    }
}
