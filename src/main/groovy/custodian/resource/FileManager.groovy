package custodian.resource

import custodian.Custodian

import groovy.json.JsonException
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.nio.file.Files

import org.codehaus.groovy.runtime.typehandling.GroovyCastException

final class FileManager {

    // JSON reader and writer, to be instantiated the first time they're needed.
    public static final JsonOutput JO = new JsonOutput()
    public static final JsonSlurper JS = new JsonSlurper()

    private static final String PROGRAM_DIR = System.getProperty('user.dir')

    private FileManager() {
        // Private default constructor to prevent instantiation.
    }

    /**
     * * * * * * * * * * *
     *   Read  Methods   *
     * * * * * * * * * * *
     */

    /**
     * Returns a reference to a file in the resources folder.
     * @param filePath The path of the file to get. This should be relative to src/main/resources.
     * @return The file.
     */
    static File get(String filePath) {
        File f = new File("$PROGRAM_DIR/src/main/resources/$filePath")
        if(!f.exists()) {
            if(!f.parentFile.exists()) {
                f.parentFile.mkdirs()
            }
            f.createNewFile()
        }
        return f
    }

    /**
     * Reads and returns the contents of a file in the resources folder.
     * @param filePath The path of the file to read. This should be relative to src/main/resources.
     * @return A string containing the contents of the file to be read, or an empty string if it doesn't exist.
     */
    static String read(String filePath) {
        File f = get(filePath)
        if(!f.exists()) {
            Custodian.LOGGER.error("The file '/src/main/resources/$filePath' does not exist to read.")
            return ''
        }
        return f.getText()
    }

    /**
     * Reads and returns the contents of a JSON-formatted file in the resources folder, with each line parsed to one element of a list.
     * @param filePath The path of the file to read. This should be relative to src/main/resources.
     * @return A list containing the parsed contents of the file to be read, or an empty list if it can't be parsed.
     */
    static List readAsLineCollection(String filePath) {
        int line = 1
        try {
            List lines = read(filePath).split('\n')
            for(line; line <= lines.size(); line++) {
                lines[line - 1] = JS.parseText(lines[line - 1])
            }
            return lines
        }
        catch(JsonException | IllegalArgumentException e) {
            Custodian.LOGGER.error("Couldn't parse line $line of '/src/main/resources/$filePath' to JSON. " +
                "Returning an empty list instead.")
            return []
        }
    }

    /**
     * Reads and returns the contents of a JSON-formatted file in the resources folder, parsed to a list.
     * @param filePath The path of the file to read. This should be relative to src/main/resources.
     * @return A list containing the parsed contents of the file to be read, or an empty list if it can't be parsed.
     */
    static List readAsList(String filePath) {
        try {
            return JS.parseText(read(filePath))
        }
        catch(JsonException | IllegalArgumentException e) {
            Custodian.LOGGER.error("Could not parse '/src/main/resources/$filePath' to JSON. Returning an empty list instead.")
            return []
        }
        catch(GroovyCastException e) {
            Custodian.LOGGER.error("'/src/main/resources/$filePath' parses to something other than a list.")
            return []
        }
    }

    /**
     * Reads and returns the contents of a JSON-formatted file in the resources folder, parsed to a map.
     * @param filePath The path of the file to read. This should be relative to src/main/resources.
     * @return A map containing the parsed contents of the file to be read, or an empty map if it can't be parsed.
     */
    static Map readAsMap(String filePath) {
        try {
            return JS.parseText(read(filePath))
        }
        catch(JsonException | IllegalArgumentException e) {
            Custodian.LOGGER.error("Could not parse '/src/main/resources/$filePath' to JSON. Returning an empty map instead.")
            return [:]
        }
        catch(GroovyCastException e) {
            Custodian.LOGGER.error("'/src/main/resources/$filePath' parses to something other than a map.")
            return [:]
        }
    }

    /**
     * Reads and returns the contents of a JSON-formatted file in the resources folder, parsed to a set.
     * @param filePath The path of the file to read. This should be relative to src/main/resources.
     * @return A set containing the parsed contents of the file to be read, or an empty set if it can't be parsed.
     */
    static Set readAsSet(String filePath) {
        try {
            return JS.parseText(read(filePath))
        }
        catch(JsonException | IllegalArgumentException e) {
            Custodian.LOGGER.error("Could not parse '/src/main/resources/$filePath' to JSON. Returning an empty set instead.")
            return [] as Set
        }
        catch(GroovyCastException e) {
            Custodian.LOGGER.error("'/src/main/resources/$filePath' parses to something other than a set.")
            return [] as Set
        }
    }

    /**
     * * * * * * * * * * *
     *   Write Methods   *
     * * * * * * * * * * *
     */

    /**
     * Writes a string of text to a file, overwriting its current contents. Protects against writing
     * over a non-empty file with an empty string or whitespace.
     * @param filePath The file to write to, relative to /src/main/resources.
     * @param text The text to write.
     * @return True if the text was written, or false if not.
     */
    static boolean write(String filePath, String text) {
        File f = get(filePath)
        if(!text.trim() && Files.size(f.toPath()) > 0) {
            Custodian.LOGGER.error("Can't overwrite non-empty file '/src/main/resources/$filePath' with an empty string or whitespace.")
            return false
        }
        else {
            f.withWriter { writer ->
                writer.write(text)
            }
            return true
        }
    }

    /**
     * Formats an object as JSON and writes it to a file, overwriting its current contents. Protects
     * against writing over a non-empty file with an empty string or whitespace.
     * @param filePath The file to write to, relative to /src/main/resources.
     * @param text The text to write.
     * @param pretty Whether or not to pretty print the JSON for readability.
     * @return True if the text was written, or false if not.
     */
    static boolean writeJson(String filePath, Object object, boolean pretty = false) {
        File f = get(filePath)
        if(!object && Files.size(f.toPath()) > 0) {
            Custodian.LOGGER.error("Can't overwrite non-empty file '/src/main/resources/$filePath' with an empty string or whitespace.")
            return false
        }
        else {
            f.withWriter { writer ->
                writer.write(pretty ? JO.prettyPrint(JO.toJson(object)) : JO.toJson(object))
            }
            return true
        }
    }

    /**
     * Formats a list as a collection of lines with one object per line and writes it to a file, overwriting
     * its current contents. Protects against writing over a non-empty file with an empty string or whitespace.
     * @param filePath The file to write to, relative to /src/main/resources.
     * @param text The text to write.
     * @return True if the text was written, or false if not.
     */
    static boolean writeLineCollection(String filePath, List object) {
        File f = get(filePath)
        if(!object.size() && Files.size(f.toPath()) > 0) {
            Custodian.LOGGER.error("Can't overwrite non-empty file '/src/main/resources/$filePath' with an empty string or whitespace.")
            return false
        }
        else {
            f.withWriter { writer ->
                for(item in object) {
                    writer.write(JO.toJson(item) + '\n')
                }
            }
            return true
        }
    }
}
