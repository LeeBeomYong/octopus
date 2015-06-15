package kr.co.bitnine.octopus.schema.model;

import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

@PersistenceCapable
public class MDataSource {
    @PrimaryKey
    @Persistent(valueStrategy= IdGeneratorStrategy.INCREMENT)
    long ID;

    @Persistent
    String name;

    @Persistent
    int type;

    @Persistent
    String jdbc_driver;

    @Persistent
    String jdbc_connectionString;

    @Persistent
    String description;
}
