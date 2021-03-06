package io.shuidi.snowflake.server;

import com.alibaba.fastjson.JSONObject;
import io.shuidi.snowflake.client.SnowflakeClient;
import io.shuidi.snowflake.core.config.SnowflakeConfig;
import io.shuidi.snowflake.core.service.Partner;
import io.shuidi.snowflake.core.util.zk.ZkUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.CloseableUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.web.context.WebApplicationContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Author: Alvin Tian
 * Date: 2017/8/28 22:48
 */
public class SnowflakeServerMultTest {

	@Test
	public void testLeaderSelect() throws Exception {
		ZkUtils zkConfig = new ZkUtils();
		zkConfig.setConnectionString("127.0.0.1:2181");
		zkConfig.setConnectionTimeoutMs(1000);
		zkConfig.setSessionTimeoutMs(10000);

		SnowflakeConfig snowflakeConfig = new SnowflakeConfig();
		snowflakeConfig.setDatacenterId(0);
		snowflakeConfig.setLeaderPath("/snowtest/leader");
		snowflakeConfig.setSkipSanityChecks(true);
		snowflakeConfig.setWorkerIdZkPath("/snowtest/servers");
		snowflakeConfig.setServer(SnowflakeServer.getHostname());
		int serverSize = 100;
		List<CuratorFramework> zkClients = new ArrayList<>();
		List<SnowflakeServer> snowflakeServers = new ArrayList<>();
		CountDownLatch countDownLatch = new CountDownLatch(serverSize);
		ExecutorService executorService = Executors.newCachedThreadPool();
		for (int i = 0; i < serverSize; i++) {
			SnowflakeConfig sfc = new SnowflakeConfig();
			BeanUtils.copyProperties(snowflakeConfig, sfc);
			sfc.setPort(i + 8800);
			CuratorFramework zkClient = zkConfig.create();
			zkClients.add(zkClient);

			SnowflakeServer snowflakeServer = new SnowflakeServer();
			snowflakeServer.setClient(zkClient);

			executorService.execute(() -> {
				try {
					countDownLatch.await();
					snowflakeServer.start();
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
			snowflakeServers.add(snowflakeServer);
			countDownLatch.countDown();
		}

		Thread.sleep(60000);

		for (SnowflakeServer snowflakeServer : snowflakeServers) {
			CloseableUtils.closeQuietly(snowflakeServer);
		}

	}

	@Test
	public void testFlow() throws Exception {
		int serverSize = 10;
		ExecutorService executorService = Executors.newCachedThreadPool();
		ConcurrentLinkedQueue<Process> processes = new ConcurrentLinkedQueue<Process>();
		CountDownLatch countDownLatch = new CountDownLatch(serverSize);
		SnowflakeClient snowflakeClient = new SnowflakeClient();
		CountDownLatch finalCountDownLatch1 = countDownLatch;


		for (int i = 0; i < serverSize; i++) {
			int finalI = i;
			executorService.execute(() -> {
				try {
					Process process = Runtime.getRuntime()
					                         .exec("java -jar target/snowflake-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=local --server.port=" +
					                               (8800 + finalI));
					processes.add(process);
					finalCountDownLatch1.countDown();
				} catch (IOException e) {
					e.printStackTrace();
				}
			});

		}
		countDownLatch.await();
		for (Process process : processes) {
			executorService.execute(() -> {
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String str = null;
				try {
					while ((str = bufferedReader.readLine()) != null) {
						System.out.println(str);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		}

		Thread.sleep(60000);

		countDownLatch = new CountDownLatch(serverSize);

		CountDownLatch finalCountDownLatch = countDownLatch;

		for (int i = 0; i < serverSize; i++) {
			int finalI = i;
			executorService.execute(new Runnable() {
				@Override
				public void run() {
					int port = (8800 + finalI);
					try {
						for (int j = 0; j < 1000; j++) {
							long id = snowflakeClient.getId("localhost:" + port, "partnerKey=A" + port);
							System.out.println(id);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
					finalCountDownLatch.countDown();
				}
			});
		}
		countDownLatch.await();

//		for (int i = 0; i < serverSize; i++) {
//			int port = (8800 + i);
//			System.out.println(snowflakeClient.getReport("localhost:" + port));
//		}

		for (Process process : processes) {
			process.destroy();
		}


	}

	@Test
	public void testFlow32() throws Exception {
		int serverSize = 10;
		ExecutorService executorService = Executors.newCachedThreadPool();
		ConcurrentLinkedQueue<Process> processes = new ConcurrentLinkedQueue<Process>();
		CountDownLatch countDownLatch = new CountDownLatch(serverSize);
		SnowflakeClient snowflakeClient = new SnowflakeClient();
		CountDownLatch finalCountDownLatch1 = countDownLatch;



		for (int i = 0; i < serverSize; i++) {
			int finalI = i;
			executorService.execute(() -> {
				try {
					Process process = Runtime.getRuntime()
					                         .exec("java -jar target/snowflake-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=local --server.port=" +
					                               (8800 + finalI));
					processes.add(process);
					finalCountDownLatch1.countDown();
				} catch (IOException e) {
					e.printStackTrace();
				}
			});

		}
		countDownLatch.await();
		for (Process process : processes) {
			executorService.execute(() -> {
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String str = null;
				try {
					while ((str = bufferedReader.readLine()) != null) {
						System.out.println(str);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		}

		Thread.sleep(60000);

		countDownLatch = new CountDownLatch(serverSize);

		CountDownLatch finalCountDownLatch = countDownLatch;

		for (int i = 0; i < serverSize; i++) {
			int finalI = i;
			executorService.execute(new Runnable() {
				@Override
				public void run() {
					int port = (8800 + finalI);
					try {
						for (int j = 0; j < 1000; j++) {
							long id = snowflakeClient.getId32("localhost:" + port, "partnerKey=A" + port);
							System.out.println(id);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
					finalCountDownLatch.countDown();
				}
			});
		}
		countDownLatch.await();

//		for (int i = 0; i < serverSize; i++) {
//			int port = (8800 + i);
//			System.out.println(snowflakeClient.getReport("localhost:" + port));
//		}

		for (Process process : processes) {
			process.destroy();
		}


	}


}
