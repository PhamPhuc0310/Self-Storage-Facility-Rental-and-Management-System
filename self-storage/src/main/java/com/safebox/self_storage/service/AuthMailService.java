package com.safebox.self_storage.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AuthMailService {
    private final JavaMailSender sender;
    private final String from;
    private final String baseUrl;

    public AuthMailService(JavaMailSender sender,
            @Value("${app.mail.from}") String from,
            @Value("${app.public-base-url}") String baseUrl) {
        this.sender = sender;
        this.from = from;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public void send(String email, String name, String token, boolean verification) throws MessagingException {
        String label = verification ? "Xác thực tài khoản" : "Đặt lại mật khẩu";
        String link = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/stitch/ng_nh_p_ng_k_safebox_storage.html")
                .queryParam("mode", verification ? "verify" : "reset")
                .queryParam("token", token).build().encode().toUriString();
        String safeLink = HtmlUtils.htmlEscape(link);
        String html = "<div style='font-family:Arial,sans-serif;color:#131b2e;max-width:560px'>"
                + "<h2>SafeBox Storage</h2><p>Xin chào " + HtmlUtils.htmlEscape(name) + ",</p>"
                + "<p>" + (verification ? "Vui lòng xác thực email để sử dụng tài khoản. Liên kết có hiệu lực 30 phút."
                : "Bạn đã yêu cầu đặt lại mật khẩu. Liên kết có hiệu lực 15 phút.") + "</p>"
                + "<p><a href='" + safeLink + "' style='background:#0f2744;color:white;padding:12px 20px;text-decoration:none;border-radius:8px'>" + label + "</a></p>"
                + "<p>Nếu nút không hoạt động, sao chép liên kết: <a href='" + safeLink + "'>" + safeLink + "</a></p>"
                + "<p>Nếu không yêu cầu thao tác này, bạn có thể bỏ qua email.</p></div>";
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setFrom(from);
        helper.setTo(email);
        helper.setSubject("SafeBox Storage - " + label);
        helper.setText(html, true);
        sender.send(message);
    }
}
