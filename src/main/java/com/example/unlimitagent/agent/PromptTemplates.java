package com.example.unlimitagent.agent;

final class PromptTemplates {

    private PromptTemplates() {
    }

    static final String STAGE1_SYSTEM =
            "You are an incident analysis assistant for a payment platform.\n" +
            "Your task is to extract structured facts from a raw incident description.\n" +
            "\n" +
            "Extract and return ONLY a JSON object with these fields:\n" +
            "{\n" +
            "  \"affected_services\": [\"list of service names mentioned or implied\"],\n" +
            "  \"symptoms\": [\"list of observed symptoms\"],\n" +
            "  \"error_types\": [\"list of error types, codes, or keywords\"],\n" +
            "  \"time_context\": \"any time reference mentioned, or null\"\n" +
            "}\n" +
            "\n" +
            "Return raw JSON only. No markdown, no explanation.";

    static final String STAGE2_SYSTEM =
            "You are an incident analysis assistant for a payment platform.\n" +
            "You have access to the system architecture description and a catalogue of past incidents.\n" +
            "Your task is to match the extracted incident facts against this knowledge\n" +
            "and identify the most likely incident category and relevant context.\n" +
            "\n" +
            "Return ONLY a JSON object:\n" +
            "{\n" +
            "  \"matched_past_incident\": \"incident ID and title, or null\",\n" +
            "  \"likely_category\": \"one-line category label\",\n" +
            "  \"relevant_services\": [\"services most likely involved\"],\n" +
            "  \"context_notes\": \"brief notes on why this matches the architecture/history\"\n" +
            "}\n" +
            "\n" +
            "Return raw JSON only. No markdown, no explanation.";

    static final String STAGE2_USER_TEMPLATE =
            "Extracted facts:\n" +
            "<stage1_output>\n" +
            "\n" +
            "System architecture:\n" +
            "<system_description>\n" +
            "\n" +
            "Past incidents:\n" +
            "<past_incidents>";

    static final String STAGE3_SYSTEM =
            "You are an incident analysis assistant for a payment platform.\n" +
            "Using the structured facts and enriched context provided, generate a final incident analysis.\n" +
            "\n" +
            "You MUST return a valid JSON object matching this exact schema:\n" +
            "{\n" +
            "  \"category\": \"string — concise incident category\",\n" +
            "  \"summary\": \"string — 2-3 sentences: what is happening, who is affected, urgency\",\n" +
            "  \"severity\": \"low | medium | high\",\n" +
            "  \"hypotheses\": [\n" +
            "    {\n" +
            "      \"title\": \"string — hypothesis name\",\n" +
            "      \"reasoning\": \"string — why this is plausible given the facts\",\n" +
            "      \"next_steps\": [\"string\", \"string\", \"string\"]\n" +
            "    }\n" +
            "  ]\n" +
            "}\n" +
            "\n" +
            "Rules:\n" +
            "- severity must be exactly one of: low, medium, high\n" +
            "- hypotheses must contain 1 to 3 items\n" +
            "- next_steps must contain 2 to 3 items per hypothesis\n" +
            "- Return raw JSON only. No markdown fences, no explanation, no extra keys.";

    static final String STAGE3_USER_TEMPLATE =
            "Extracted facts:\n" +
            "<stage1_output>\n" +
            "\n" +
            "Enriched context:\n" +
            "<stage2_output>\n" +
            "\n" +
            "Generate the final incident analysis JSON now.";

    static final String RETRY_SUFFIX_TEMPLATE =
            "\n" +
            "\n" +
            "Your previous response failed validation with these errors:\n" +
            "<validation_errors>\n" +
            "\n" +
            "Please return ONLY valid JSON matching the required schema. No markdown, no explanation.";
}
