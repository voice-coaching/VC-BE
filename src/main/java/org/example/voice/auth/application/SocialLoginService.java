package org.example.voice.auth.application;

import org.example.voice.auth.domain.model.AuthSession;
import org.example.voice.auth.domain.model.SocialUserInfo;
import org.example.voice.auth.domain.port.SocialOAuthProvider;
import org.example.voice.auth.domain.type.OAuthProvider;
import org.example.voice.auth.exception.UnsupportedSocialProviderException;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SocialLoginService {
    private final Map<OAuthProvider, SocialOAuthProvider> providers;
    private final SocialAccountService socialAccountService;

    public SocialLoginService(List<SocialOAuthProvider> providers, SocialAccountService socialAccountService) {
        this.providers = new EnumMap<>(OAuthProvider.class);
        providers.forEach(provider -> this.providers.put(provider.provider(), provider));
        this.socialAccountService = socialAccountService;
    }

    public AuthSession login(String providerName, String authorizationCode, String redirectUri) {
        OAuthProvider provider = parseProvider(providerName);
        SocialOAuthProvider client = providers.get(provider);
        if (client == null) throw new UnsupportedSocialProviderException();
        SocialUserInfo profile = client.authenticate(authorizationCode, redirectUri);
        return socialAccountService.completeLogin(provider, profile);
    }

    private OAuthProvider parseProvider(String value) {
        try {
            OAuthProvider provider = OAuthProvider.valueOf(value.toUpperCase(Locale.ROOT));
            if (provider != OAuthProvider.GOOGLE && provider != OAuthProvider.KAKAO) throw new UnsupportedSocialProviderException();
            return provider;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new UnsupportedSocialProviderException();
        }
    }
}
