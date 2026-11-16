package com.ispbilling.web;

import com.ispbilling.common.BusinessException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** Panel denetleyicileri için ortak yardımcılar. */
abstract class PageSupport {

    /** İşlemi çalıştırır; başarılıysa yeşil, iş kuralı hatasıysa kırmızı mesajla yönlendirir. */
    protected String attempt(RedirectAttributes ra, String successMessage, String redirectTo, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("success", successMessage);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + redirectTo;
    }

    /** Doğrulama hatalarını kullanıcıya gösterilecek metinlere çevirir. */
    protected void addErrors(Model model, BindingResult br) {
        List<String> errors = br.getFieldErrors().stream()
                .map(f -> "typeMismatch".equals(f.getCode())
                        ? f.getField() + ": geçersiz değer \"" + f.getRejectedValue() + "\""
                        : f.getDefaultMessage())
                .distinct()
                .toList();
        model.addAttribute("errors", errors);
    }
}
