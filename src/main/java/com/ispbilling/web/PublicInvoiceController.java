package com.ispbilling.web;

import com.ispbilling.common.NotFoundException;
import com.ispbilling.invoice.InvoiceDocumentService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Abonenin e-posta/SMS ile aldığı bağlantıdan açtığı fatura sayfası. Giriş gerektirmez.
 * Kurulumda internete yalnızca bu yol (/f/**) ve /css/** açılır; panel iç ağda kalır.
 */
@Controller
public class PublicInvoiceController {

    private final InvoiceDocumentService documents;

    public PublicInvoiceController(InvoiceDocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/f/{token}")
    String invoice(@PathVariable String token, Model model, HttpServletResponse response) {
        if (!token.matches("[0-9a-f]{64}")) {
            throw new NotFoundException("Fatura", "bağlantı");
        }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
        response.setHeader("Referrer-Policy", "no-referrer");
        model.addAttribute("doc", documents.byToken(token));
        return "public/invoice";
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound() {
        return "public/not-found";
    }
}
