package com.example.backend.security;

import com.example.backend.common.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class AuthService {
    private final Store db;
    private final PasswordEncoder passwords;
    private final String secret;
    private final PasswordResetDelivery delivery;
    public AuthService(Store db, PasswordEncoder passwords, @Value("${fleetflow.jwt-secret}") String secret,PasswordResetDelivery delivery) { this.db=db; this.passwords=passwords; this.secret=secret;this.delivery=delivery; }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String randomToken() { byte[] bytes=new byte[48]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    @Transactional public Map<String,Object> register(String name, String email, String password) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO app_user(id,name,email,password_hash,role) VALUES(?,?,?,?,'CUSTOMER')",id,name,email.toLowerCase(Locale.ROOT),passwords.encode(password));
        db.audit(id,"REGISTER",id); return tokens(db.one("SELECT * FROM app_user WHERE id=?",id));
    }
    @Transactional public Map<String,Object> login(String email, String password) {
        var users=db.rows("SELECT * FROM app_user WHERE email=? FOR UPDATE",email.toLowerCase(Locale.ROOT));
        if (users.isEmpty() || !passwords.matches(password,users.getFirst().get("password_hash").toString()) || !Boolean.TRUE.equals(users.getFirst().get("active")))
            throw new ApiException(401,"INVALID_CREDENTIALS","Invalid email or password");
        var user=users.getFirst(); db.audit(Store.id(user,"id"),"LOGIN",user.get("id")); return tokens(user);
    }
    @Transactional public Map<String,Object> refresh(String token) {
        var found=db.rows("DELETE FROM auth_token WHERE token_hash=? AND kind='REFRESH' AND expires_at>now() RETURNING user_id",hash(token));
        if(found.isEmpty()) throw new ApiException(401,"INVALID_REFRESH","Refresh token is expired or already used");
        var user=db.one("SELECT * FROM app_user WHERE id=? FOR UPDATE",found.getFirst().get("user_id"));
        if (!Boolean.TRUE.equals(user.get("active"))) throw ApiException.forbidden();
        return tokens(user);
    }
    private Map<String,Object> tokens(Map<String,Object> user) {
        UUID id=Store.id(user,"id"); Instant now=Instant.now();
        try {
            var claims=new JWTClaimsSet.Builder().issuer("fleetflow").subject(id.toString()).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(900)))
                .claim("version",Store.integer(user,"token_version")).jwtID(UUID.randomUUID().toString()).build();
            var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims); jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            String refresh=randomToken();
            db.update("INSERT INTO auth_token(token_hash,user_id,kind,expires_at) VALUES(?,?,'REFRESH',?)",hash(refresh),id,java.sql.Timestamp.from(now.plus(Duration.ofDays(7))));
            return Map.of("accessToken",jwt.serialize(),"refreshToken",refresh,"expiresIn",900,"user",profile(id));
        } catch(JOSEException e) { throw new IllegalStateException("Token signing failed",e); }
    }
    public Map<String,Object> profile(UUID id) { return db.one("SELECT id,name,email,role,active,force_reset,created_at FROM app_user WHERE id=?",id); }
    @Transactional public void logout(UUID id) { revoke(id); db.audit(id,"LOGOUT",id); }
    private void revoke(UUID id) { db.update("UPDATE app_user SET token_version=token_version+1 WHERE id=?",id); db.update("DELETE FROM auth_token WHERE user_id=?",id); }
    @Transactional public void password(UUID id,String current,String next) {
        var user=db.one("SELECT * FROM app_user WHERE id=? FOR UPDATE",id);
        if (!passwords.matches(current,user.get("password_hash").toString())) throw new ApiException(400,"WRONG_PASSWORD","Current password is incorrect");
        db.update("UPDATE app_user SET password_hash=?,force_reset=false WHERE id=?",passwords.encode(next),id); revoke(id); db.audit(id,"PASSWORD_CHANGED",id);
    }
    @Transactional public void forgot(String email) {
        delivery.requireConfigured();
        var users=db.rows("SELECT id FROM app_user WHERE email=? AND active=true",email.toLowerCase(Locale.ROOT));
        if (!users.isEmpty()) {
            UUID id=Store.id(users.getFirst(),"id");
            db.one("SELECT id FROM app_user WHERE id=? FOR UPDATE",id);
            db.update("DELETE FROM auth_token WHERE user_id=? AND kind='RESET'",id);
            String token=randomToken();
            db.update("INSERT INTO auth_token(token_hash,user_id,kind,expires_at) VALUES(?,?,'RESET',?)",hash(token),id,java.sql.Timestamp.from(Instant.now().plusSeconds(1200)));
            delivery.send(email,token);
        }
    }
    @Transactional public void reset(String token,String password) {
        var rows=db.rows("DELETE FROM auth_token WHERE token_hash=? AND kind='RESET' AND expires_at>now() RETURNING user_id",hash(token));
        if(rows.isEmpty()) throw new ApiException(400,"INVALID_RESET","Invalid or expired password reset token");
        UUID id=Store.id(rows.getFirst(),"user_id"); db.update("UPDATE app_user SET password_hash=?,force_reset=false WHERE id=?",passwords.encode(password),id); revoke(id); db.audit(id,"PASSWORD_RESET",id);
    }
}
