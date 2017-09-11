package custodian.resource

import custodian.Custodian

import groovy.text.GStringTemplateEngine

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

final class TemplateManager {

    private static final GStringTemplateEngine GSTE = new GStringTemplateEngine()
    private static final Map KNOWN_TYPES = [
        'channel': '(?<=<#)\\d+(?=>)',
        'role': '(?<=<@&)\\d+(?=>)',
        'user': '(?<=<@)\\d+(?=>)',
        'number': '(-|)\\d(\\.\\d|)',
        'integer': '(-|)\\d',
        'negative_number': '-\\d(\\.\\d|)',
        'negative_integer': '-\\d',
        'positive_number': '\\d(\\.\\d|)',
        'positive_integer': '\\d'
    ]

    private TemplateManager() {
        // Private default constructor to prevent instantiation.
    }

    // Get data from input e.g. "~link add {link:https://[^ ]+}[ ]+{title:[^ ]+([ ]+[^ ]+)*}" and return it in a map
    // Take data from a map and put it into a template (probably the easy part)

    // User ping looks like <@id>. Role ping looks like <@&id>. Channel ping looks like <#id>.

    static boolean validateTemplate(String template) {
        String varName
        int matchLength
        List usedNames = []
        StringBuilder builder = new StringBuilder()
        for(int pos = 0; pos < template.size(); pos++) {
            if(template[pos] == '$') {
                if(template[pos + 1] == '{') {
                    pos += 2
                    (varName, matchLength) = getVariableName(builder, template.substring(pos))
                    if(matchLength == -1) {
                        Custodian.LOGGER.error("Could not validate template string '$template'. Error found at or near position ${pos}.")
                        return false
                    }
                    if(varName in usedNames) {
                        Custodian.LOGGER.error("Could not validate template string '$template'. Variable $varName is used more than once.")
                        return false
                    }
                    pos += matchLength
                    usedNames << varName
                    (varName, matchLength) = getVariableMatchString(builder, template.substring(pos))
                    if(matchLength == -1) {
                        Custodian.LOGGER.error("Could not validate template string '$template'. Error found at or near position ${pos}.")
                        return false
                    }
                    pos += matchLength
                }
                else {
                    continue
                }
            }
            else {
                continue
            }
        }
        return true
    }

    static List parseTemplate(String template) {
        if(!validateTemplate(template)) {
            return []
        }
        StringBuilder builder = new StringBuilder()
        List templateSegments = []
        String varName, varMatchString
        int matchLength
        for(int pos = 0; pos < template.size(); pos++) {
            if(template[pos] == '$') {
                if(template[pos + 1] == '{') {
                    pos += 2
                    if(builder.size()) {
                        templateSegments << new TemplateSegment('', builder.toString().trim())
                        builder.replace(0, builder.size(), '')
                    }
                    (varName, matchLength) = getVariableName(builder, template.substring(pos))
                    pos += matchLength
                    (varMatchString, matchLength) = getVariableMatchString(builder, template.substring(pos))
                    pos += matchLength
                    templateSegments << new TemplateSegment(varName, varMatchString)
                }
                else {
                    builder.append(template[pos])
                }
            }
            else {
                builder.append(template[pos])
            }
        }
        if(builder.size()) {
            templateSegments << new TemplateSegment('', builder.toString().trim())
        }
        return templateSegments
    }

    static Map extractData(List segments, String inputString) {
        Map data = [:]
        String input = inputString.trim()
        for(int pos = 0; pos < segments.size(); pos++) {
            TemplateSegment seg = segments[pos]
            String match = input.find(seg.match)
            if(match == null) {
                data['_failed'] = true
                data['_failedOn'] = pos
                return data
            }
            switch(seg.match) {
                case KNOWN_TYPES.channel:
                case KNOWN_TYPES.user:
                    input = input.substring(match.size() + 3)
                    break
                case KNOWN_TYPES.role:
                    input = input.substring(match.size() + 4)
                    break
                default:
                    input = input.substring(match.size())
            }
            if(seg.name) {
                data[seg.name] = match
            }
        }
        return data
    }

    static String fillTemplateWithData(Map data, String templateString) {
        try {
            return GSTE.createTemplate(templateString).make(data).toString()
        }
        catch(MissingPropertyException e) {
            Custodian.LOGGER.error("Error filling '$templateString' with data. Template property '$e.property' was not found in data.")
            return ''
        }
    }

    private static List getVariableName(StringBuilder builder, String template) {
        boolean hasSeenWord = false
        for(int pos = 0; pos < template.size(); pos++) {
            char ch = template[pos]
            if(!hasSeenWord) {
                if(ch == ' ') {
                    continue
                }
                else if((ch > 64 && ch < 91) || (ch > 96 && ch < 123)) { // If it's in [a-zA-Z]
                    builder.append(ch)
                    hasSeenWord = true
                }
                else {
                    return ['', -1]
                }
            }
            else {
                if((ch > 64 && ch < 91) || (ch > 96 && ch < 123) || (ch > 47 && ch < 58) || ch == '_') {
                    builder.append(ch)
                }
                else if(ch == ' ' || ch == ':') {
                    while(ch == ' ') {
                        pos++
                        ch = template[pos]
                    }
                    if(ch != ':') {
                        return ['', -1]
                    }
                    pos++ // Skip over the :
                    String name = builder.toString()
                    builder.replace(0, builder.size(), '')
                    return [name, pos]
                }
                else {
                    return ['', -1]
                }
            }
        }
    }

    private static List getVariableMatchString(StringBuilder builder, String template) {
        int unmatchedBraces = 1
        int pos = 0
        for(; pos < template.size(); pos++) {
            char ch = template[pos]
            if(ch == '{') {
                unmatchedBraces++
                builder.append(ch)
            }
            else if(ch == '}') {
                unmatchedBraces--
                if(unmatchedBraces > 0) {
                    builder.append(ch)
                }
                else {
                    pos++ // Skip over the ending }
                    break
                }
            }
            else {
                builder.append(ch)
            }
        }
        String match = builder.toString().trim() // Trim. To match regex with leading or trailing whitespace, enclose pattern in ' or /
        if((match[0] == '\'' && match[match.size()] == '\'') || (match[0] == '/' && match[match.size()] == '/')) {
            match = match.substring(1, match.size() - 1)
        }
        if(KNOWN_TYPES[match]) {
            match = KNOWN_TYPES[match]
        }
        else if(!validateRegex(match)) {
            return ['', -1]
        }
        builder.replace(0, builder.size(), '')
        return [match, pos]
    }

    private static boolean validateRegex(String regexString) {
        try {
            Pattern.compile(regexString)
            return true
        }
        catch(PatternSyntaxExceptione) {
            return false
        }
    }

    static class TemplateSegment {
        String name // Name of the variable to store this segment to.
        String match // The text this segment should match

        TemplateSegment(String name, String match) {
            this.name = name
            this.match = match
        }
    }
}
