package usv.dizertatie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DizertatieApplication {

	public static void main(String[] args) {
		SpringApplication.run(DizertatieApplication.class, args);
	}

}
