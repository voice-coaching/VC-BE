package org.example.voice.practicecontent;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class ContentCatalogPostgresMigrationTest {
    @Test void freshDatabaseInstallsTaxonomyAndIndex() throws Exception {verify(false);}
    @Test void upgradePreservesOriginalContentWithoutInventingPublisher() throws Exception {verify(true);}
    private void verify(boolean upgrade) throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL"),user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres"),password=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD","");
        String schema="catalog_test_"+UUID.randomUUID().toString().replace("-","");
        try(var db=DriverManager.getConnection(url,user,password);var sql=db.createStatement()){
            try {
                if(upgrade){
                    migrate(url,user,password,schema,"22");db.setSchema(schema);
                    sql.executeUpdate("insert into practice_contents(id,content_type,learning_focus,title,script_text,difficulty,status,created_at,updated_at) values (700001,'NEWS','BOTH','original title','original text','BEGINNER','PUBLISHED',now(),now())");
                }
                migrate(url,user,password,schema,"23");db.setSchema(schema);
                try(var rows=sql.executeQuery("select count(*) from content_categories")){rows.next();assertThat(rows.getInt(1)).isEqualTo(2);}
                try(var rows=sql.executeQuery("select count(*) from pg_indexes where schemaname=current_schema() and indexname='idx_content_catalog_order'")){rows.next();assertThat(rows.getInt(1)).isEqualTo(1);}
                assertThatThrownBy(()->sql.executeUpdate("insert into content_categories(content_type,code,label,sort_order) values ('NEWS','SOCIETY','duplicate',99)"))
                        .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23505"));
                if(upgrade)try(var rows=sql.executeQuery("select title,script_text,publisher,speaker_name,owner_id from practice_contents where id=700001")){
                    assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("original title");assertThat(rows.getString(2)).isEqualTo("original text");
                    assertThat(rows.getObject(3)).isNull();assertThat(rows.getObject(4)).isNull();assertThat(rows.getObject(5)).isNull();
                }
            }finally{db.setSchema("public");sql.execute("drop schema if exists "+schema+" cascade");}
        }
    }
    private void migrate(String url,String user,String password,String schema,String target){
        var f=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();f.migrate();f.validate();
    }
}
