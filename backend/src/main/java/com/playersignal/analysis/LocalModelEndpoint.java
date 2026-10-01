package com.playersignal.analysis;
import java.net.URI;
import java.util.Set;
public final class LocalModelEndpoint {
    private LocalModelEndpoint() {}
    public static URI origin(String url) {
        URI root=URI.create(url);
        if(!Set.of("localhost","127.0.0.1","[::1]","host.docker.internal","ollama").contains(root.getHost()==null?"":root.getHost())
                || !"http".equals(root.getScheme()) || root.getUserInfo()!=null || root.getQuery()!=null || root.getFragment()!=null
                || !(root.getPath().isEmpty() || root.getPath().equals("/")))
            throw new IllegalArgumentException("OLLAMA_URL must be a local HTTP origin or the private ollama service");
        return root;
    }
}
