package specification

import au.com.dius.pact.core.model.PactReader
import groovy.json.JsonSlurper
import spock.lang.Specification

class BaseRequestSpec extends Specification {

  static List loadTestCases(String testDir) {
    def resources = BaseRequestSpec.getResource(testDir)
    def file = new File(resources.toURI())
    def result = []
    file.eachDir { d ->
      d.eachFile { f ->
        def json = new JsonSlurper().parse(f)
        def expected = PactReader.extractRequest(json.expected)
        def actual = PactReader.extractRequest(json.actual)
        if (expected.body.present) {
          expected.setDefaultMimeType(expected.detectContentType())
        }
        actual.setDefaultMimeType(actual.body.present ? actual.detectContentType() : 'application/json')
        result << [d.name, f.name, json.comment, json.match, json.match ? 'should match' : 'should not match',
                   expected, actual]
      }
    }
    result
  }

}
