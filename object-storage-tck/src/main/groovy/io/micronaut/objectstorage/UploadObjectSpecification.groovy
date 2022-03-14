package io.micronaut.objectstorage

import jakarta.inject.Inject
import spock.lang.Specification


abstract class UploadObjectSpecification extends Specification{

    @Inject
    ObjectStorage objectStorage;

    def "it can upload from file"(){
        expect:
        false
    }


}
