package pt.sin.services.unlimitagent.agent;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class PromptTemplates {

    private static final String RESOURCE_STAGE1_SYSTEM  = "classpath:prompts/stage1_system.txt";
    private static final String RESOURCE_STAGE2_SYSTEM  = "classpath:prompts/stage2_system.txt";
    private static final String RESOURCE_STAGE3_SYSTEM  = "classpath:prompts/stage3_system.txt";
    private static final String RESOURCE_STAGE2_USER    = "classpath:prompts/stage2_user_template.txt";
    private static final String RESOURCE_STAGE3_USER    = "classpath:prompts/stage3_user_template.txt";
    private static final String RESOURCE_RETRY_SUFFIX   = "classpath:prompts/retry_suffix_template.txt";

    private static final String VAR_STAGE1_OUTPUT       = "stage1Output";
    private static final String VAR_STAGE2_OUTPUT       = "stage2Output";
    private static final String VAR_SYSTEM_DESCRIPTION  = "systemDescription";
    private static final String VAR_PAST_INCIDENTS      = "pastIncidents";
    private static final String VAR_VALIDATION_ERRORS   = "validationErrors";

    private final String stage1System;
    private final String stage2System;
    private final String stage3System;
    private final String stage2UserTemplate;
    private final String stage3UserTemplate;
    private final String retrySuffixTemplate;
    private final SpringTemplateEngine templateEngine;

    public PromptTemplates(ResourceLoader resourceLoader) {
        this.stage1System       = load(resourceLoader, RESOURCE_STAGE1_SYSTEM);
        this.stage2System       = load(resourceLoader, RESOURCE_STAGE2_SYSTEM);
        this.stage3System       = load(resourceLoader, RESOURCE_STAGE3_SYSTEM);
        this.stage2UserTemplate = load(resourceLoader, RESOURCE_STAGE2_USER);
        this.stage3UserTemplate = load(resourceLoader, RESOURCE_STAGE3_USER);
        this.retrySuffixTemplate = load(resourceLoader, RESOURCE_RETRY_SUFFIX);
        this.templateEngine     = buildEngine();
    }

    private static SpringTemplateEngine buildEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.TEXT);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static String load(ResourceLoader loader, String location) {
        Resource resource = loader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Required prompt file not found: " + location);
        }
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("Prompt file is blank: " + location);
            }
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt file: " + location, e);
        }
    }

    public String getStage1System() {
        return stage1System;
    }

    public String getStage2System() {
        return stage2System;
    }

    public String getStage3System() {
        return stage3System;
    }

    public String renderStage2UserMessage(String stage1Output, String systemDescription, String pastIncidents) {
        Context ctx = new Context();
        ctx.setVariable(VAR_STAGE1_OUTPUT, stage1Output);
        ctx.setVariable(VAR_SYSTEM_DESCRIPTION, systemDescription);
        ctx.setVariable(VAR_PAST_INCIDENTS, pastIncidents);
        return templateEngine.process(stage2UserTemplate, ctx);
    }

    public String renderStage3UserMessage(String stage1Output, String stage2Output) {
        Context ctx = new Context();
        ctx.setVariable(VAR_STAGE1_OUTPUT, stage1Output);
        ctx.setVariable(VAR_STAGE2_OUTPUT, stage2Output);
        return templateEngine.process(stage3UserTemplate, ctx);
    }

    public String renderRetrySuffix(String validationErrors) {
        Context ctx = new Context();
        ctx.setVariable(VAR_VALIDATION_ERRORS, validationErrors);
        return templateEngine.process(retrySuffixTemplate, ctx);
    }
}
