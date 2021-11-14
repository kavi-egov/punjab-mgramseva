package org.egov.wscalculation.consumer;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.egov.wscalculation.config.WSCalculationConfiguration;
import org.egov.wscalculation.constants.WSCalculationConstant;
import org.egov.wscalculation.validator.WSCalculationValidator;
import org.egov.wscalculation.validator.WSCalculationWorkflowValidator;
import org.egov.wscalculation.web.models.Action;
import org.egov.wscalculation.web.models.ActionItem;
import org.egov.wscalculation.web.models.BulkDemand;
import org.egov.wscalculation.web.models.CalculationCriteria;
import org.egov.wscalculation.web.models.CalculationReq;
import org.egov.wscalculation.web.models.Category;
import org.egov.wscalculation.web.models.Event;
import org.egov.wscalculation.web.models.EventRequest;
import org.egov.wscalculation.web.models.OwnerInfo;
import org.egov.wscalculation.web.models.Recipient;
import org.egov.wscalculation.web.models.SMSRequest;
import org.egov.wscalculation.web.models.Source;
import org.egov.wscalculation.web.models.users.UserDetailResponse;
import org.egov.wscalculation.producer.WSCalculationProducer;
import org.egov.wscalculation.repository.WSCalculationDao;
import org.egov.wscalculation.service.EstimationService;
import org.egov.wscalculation.service.MasterDataService;
import org.egov.wscalculation.service.UserService;
import org.egov.wscalculation.service.WSCalculationServiceImpl;
import org.egov.wscalculation.util.CalculatorUtil;
import org.egov.wscalculation.util.NotificationUtil;
import org.egov.wscalculation.util.WSCalculationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DemandGenerationConsumer {

	@Autowired
	private ObjectMapper mapper;

	@Autowired
	private WSCalculationConfiguration config;

	@Autowired
	private WSCalculationServiceImpl wSCalculationServiceImpl;

	@Autowired
	private WSCalculationProducer producer;

	@Autowired
	private MasterDataService mDataService;

	@Autowired
	private WSCalculationWorkflowValidator wsCalulationWorkflowValidator;

	@Autowired
	private NotificationUtil util;

	@Autowired
	private CalculatorUtil calculatorUtils;

	@Autowired
	private WSCalculationDao waterCalculatorDao;

	@Autowired
	private EstimationService estimationService;

	@Autowired
	private WSCalculationProducer wsCalculationProducer;

	@Autowired
	private UserService userService;

	@Autowired
	private WSCalculationUtil wsCalculationUtil;

	@Autowired
	private WSCalculationValidator wsCalculationValidator;

	/**
	 * Listen the topic for processing the batch records.
	 * 
	 * @param records would be calculation criteria.
	 */
	@KafkaListener(topics = {
			"${egov.watercalculatorservice.createdemand.topic}" }, containerFactory = "kafkaListenerContainerFactoryBatch")
	public void listen(final List<Message<?>> records) {
		CalculationReq calculationReq = mapper.convertValue(records.get(0).getPayload(), CalculationReq.class);
		Map<String, Object> masterMap = mDataService.loadMasterData(calculationReq.getRequestInfo(),
				calculationReq.getCalculationCriteria().get(0).getTenantId());
		List<CalculationCriteria> calculationCriteria = new ArrayList<>();
		boolean isSendMessage = false;
		records.forEach(record -> {
			try {
				CalculationReq calcReq = mapper.convertValue(record.getPayload(), CalculationReq.class);
				calculationCriteria.addAll(calcReq.getCalculationCriteria());
			} catch (final Exception e) {
				StringBuilder builder = new StringBuilder();
				try {
					builder.append("Error while listening to value: ").append(mapper.writeValueAsString(record))
							.append(" on topic: ").append(e);
				} catch (JsonProcessingException e1) {
					log.error("KAFKA_PROCESS_ERROR", e1);
				}
				log.error(builder.toString());
			}
		});
		CalculationReq request = CalculationReq.builder().calculationCriteria(calculationCriteria)
				.requestInfo(calculationReq.getRequestInfo()).isconnectionCalculation(true).build();
		try {
			generateDemandInBatch(request, masterMap, config.getDeadLetterTopicBatch(), isSendMessage);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		log.info("Number of batch records:  " + records.size());
	}

	/**
	 * Listens on the dead letter topic of the bulk request and processes every
	 * record individually and pushes failed records on error topic
	 * 
	 * @param records failed batch processing
	 */
	@KafkaListener(topics = {
			"${persister.demand.based.dead.letter.topic.batch}" }, containerFactory = "kafkaListenerContainerFactory")
	public void listenDeadLetterTopic(final List<Message<?>> records) {
		CalculationReq calculationReq = mapper.convertValue(records.get(0).getPayload(), CalculationReq.class);
		Map<String, Object> masterMap = mDataService.loadMasterData(calculationReq.getRequestInfo(),
				calculationReq.getCalculationCriteria().get(0).getTenantId());
		boolean isSendMessage = false;
		records.forEach(record -> {
			try {
				CalculationReq calcReq = mapper.convertValue(record.getPayload(), CalculationReq.class);

				calcReq.getCalculationCriteria().forEach(calcCriteria -> {
					CalculationReq request = CalculationReq.builder().calculationCriteria(Arrays.asList(calcCriteria))
							.requestInfo(calculationReq.getRequestInfo()).isconnectionCalculation(true).build();
					try {
						log.info("Generating Demand for Criteria : " + mapper.writeValueAsString(calcCriteria));
						// processing single
						generateDemandInBatch(request, masterMap, config.getDeadLetterTopicSingle(), isSendMessage);
					} catch (final Exception e) {
						StringBuilder builder = new StringBuilder();
						try {
							builder.append("Error while generating Demand for Criteria: ")
									.append(mapper.writeValueAsString(calcCriteria));
						} catch (JsonProcessingException e1) {
							log.error("KAFKA_PROCESS_ERROR", e1);
						}
						log.error(builder.toString(), e);
					}
				});
			} catch (final Exception e) {
				StringBuilder builder = new StringBuilder();
				builder.append("Error while listening to value: ").append(record).append(" on dead letter topic.");
				log.error(builder.toString(), e);
			}
		});
	}

	/**
	 * Generate demand in bulk on given criteria
	 * 
	 * @param request       Calculation request
	 * @param masterMap     master data
	 * @param errorTopic    error topic
	 * @param isSendMessage
	 */
	private void generateDemandInBatch(CalculationReq request, Map<String, Object> masterMap, String errorTopic,
			boolean isSendMessage) throws Exception {
		for (CalculationCriteria criteria : request.getCalculationCriteria()) {
			Boolean genratedemand = true;
			wsCalulationWorkflowValidator.applicationValidation(request.getRequestInfo(), criteria.getTenantId(),
					criteria.getConnectionNo(), genratedemand);
		}
		wSCalculationServiceImpl.bulkDemandGeneration(request, masterMap);
		String connectionNoStrings = request.getCalculationCriteria().stream()
				.map(criteria -> criteria.getConnectionNo()).collect(Collectors.toSet()).toString();
		StringBuilder str = new StringBuilder("Demand generated Successfully. For records : ")
				.append(connectionNoStrings);
//			producer.push(errorTopic, request);
//			remove the try catch or throw the exception to the previous method to catch it.

	}

	/**
	 * 
	 * @param tenantId TenantId for getting master data.
	 */
	@KafkaListener(topics = {
			"${egov.wscal.bulk.demand.schedular.topic}" }, containerFactory = "kafkaListenerContainerFactory")
	public void generateDemandForTenantId(HashMap<Object, Object> messageData) {
		String tenantId;
		RequestInfo requestInfo;
		boolean isSendMessage;
		HashMap<Object, Object> demandData = (HashMap<Object, Object>) messageData;
		tenantId = demandData.get("tenantId").toString();
		isSendMessage = mapper.convertValue(demandData.get("isSendMessage"), boolean.class);
		requestInfo = mapper.convertValue(demandData.get("requestInfo"), RequestInfo.class);
		requestInfo.getUserInfo().setTenantId(tenantId);
		Map<String, Object> billingMasterData = calculatorUtils.loadBillingFrequencyMasterData(requestInfo, tenantId);

		generateDemandForULB(billingMasterData, requestInfo, tenantId, isSendMessage);
	}

	/**
	 * 
	 * @param master      Master MDMS Data
	 * @param requestInfo Request Info
	 * @param tenantId    Tenant Id
	 */
	@SuppressWarnings("unchecked")
	public void generateDemandForULB(Map<String, Object> master, RequestInfo requestInfo, String tenantId,
			boolean isSendMessage) {
		log.info("Billing master data values for non metered connection:: {}", master);
		long startDay = (((int) master.get(WSCalculationConstant.Demand_Generate_Date_String)) / 86400000);

		List<Event> events = new ArrayList<>();

		if (isCurrentDateIsMatching((String) master.get(WSCalculationConstant.Billing_Cycle_String), startDay)) {

			LocalDate firstDate = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
			LocalDate lastDate = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());

			DateTimeFormatter formatters = DateTimeFormatter.ofPattern("d/MM/uuuu");
			String fromDate = firstDate.format(formatters);
			String toDate = lastDate.format(formatters);
			String billingCycle = fromDate + " - " + toDate;
			boolean isManual = false;
			SendNotificationsToUsers(requestInfo, tenantId, billingCycle, master, isSendMessage, isManual);
			

		}
	}

	private void SendNotificationsToUsers(RequestInfo requestInfo, String tenantId, String billingCycle,
			Map<String, Object> master, boolean isSendMessage, boolean isManual) {
		// TODO Auto-generated method stub

		LocalDate todayDate = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
		Long dayStartTime = LocalDateTime
				.of(todayDate.getYear(), todayDate.getMonth(), todayDate.getDayOfMonth(), 0, 0, 0)
				.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		Long dayEndTime = LocalDateTime
				.of(todayDate.getYear(), todayDate.getMonth(), todayDate.getDayOfMonth(), 23, 59, 59)
				.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

		List<String> connectionNos = waterCalculatorDao.getNonMeterConnectionsList(tenantId, dayStartTime,
				dayEndTime);

		List<String> meteredConnectionNos = waterCalculatorDao.getConnectionsNoList(tenantId,
				WSCalculationConstant.meteredConnectionType);

		String assessmentYear = estimationService.getAssessmentYear();
		ArrayList<String> failedConnectionNos = new ArrayList<String>();
		for (String connectionNo : connectionNos) {
			CalculationCriteria calculationCriteria = CalculationCriteria.builder().tenantId(tenantId)
					.assessmentYear(assessmentYear).connectionNo(connectionNo).build();
			List<CalculationCriteria> calculationCriteriaList = new ArrayList<>();
			calculationCriteriaList.add(calculationCriteria);
			CalculationReq calculationReq = CalculationReq.builder().calculationCriteria(calculationCriteriaList)
					.requestInfo(requestInfo).isconnectionCalculation(true).build();
			// wsCalculationProducer.push(config.getCreateDemand(), calculationReq);
			// log.info("Prepared Statement" + calculationRes.toString());

			try {
				generateDemandInBatch(calculationReq, master, billingCycle, isSendMessage);

			} catch (Exception e) {
				// TODO: handle exception
				failedConnectionNos.add(connectionNo);
			}
			// TODO need to call generatedemandinbatch method instead of pushing to kafka.
			// generate demand should be in a try catch block
			// in catch block add the current connection no to the failed Array.
			// from contoller we need to pass send message as true while calling it for
			// second time. //// need to ask phani anna how to pass it and how can we know
			// if it is second time.

		}
		System.out.println("demand Failed event Messages to the GP users ");		
		if (isSendMessage && failedConnectionNos.size() > 0  || isManual && failedConnectionNos.size() > 0 ) {
			List<ActionItem> actionItems = new ArrayList<>();
			String actionLink = config.getBulkDemandFailedLink();
			ActionItem actionItem = ActionItem.builder().actionUrl(actionLink).build();
			actionItems.add(actionItem);
			Action actions = Action.builder().actionUrls(actionItems).build();
			System.out.println("Action Link::" + actionLink);

			List<Event> event = new ArrayList<>();
			HashMap<String, String> failedMessage = util.getLocalizationMessage(requestInfo,
					WSCalculationConstant.GENERATE_DEMAND_EVENT, tenantId);
			String messages = failedMessage.get(WSCalculationConstant.MSG_KEY);
			messages = messages.replace("{BILLING_CYCLE}", LocalDate.now().getMonth().toString());
			System.out.println("Demand Genaration Failed::" + failedMessage);
			event.add(Event.builder().tenantId(tenantId).description(messages)
					.eventType(WSCalculationConstant.USREVENTS_EVENT_TYPE)
					.name(WSCalculationConstant.MONTHLY_DEMAND_FAILED)
					.postedBy(WSCalculationConstant.USREVENTS_EVENT_POSTEDBY)
					.recepient(getRecepient(requestInfo, tenantId)).source(Source.WEBAPP).eventDetails(null)
					.actions(actions).build());

			if (!CollectionUtils.isEmpty(event)) {
				EventRequest eventReq = EventRequest.builder().requestInfo(requestInfo).events(event).build();
				util.sendEventNotification(eventReq);
			}

		}
		
		System.out.println("Event Messages to the users");
		List<ActionItem> items = new ArrayList<>();
		String demandActionLink = config.getBulkDemandLink();
		ActionItem item = ActionItem.builder().actionUrl(demandActionLink).build();
		items.add(item);
		Action action = Action.builder().actionUrls(items).build();

		// Event notifications to the GP Users based on no of metered and non metered
		// connections
		List<Event> events = new ArrayList<>();

		HashMap<String, String> messageMap = new HashMap<String, String>();
		HashMap<String, Object> additionals = new HashMap<String, Object>();

		String message = null;
		if (connectionNos.size() > 0 && meteredConnectionNos.size() > 0) {
			messageMap = util.getLocalizationMessage(requestInfo, WSCalculationConstant.NEW_BULK_DEMAND_EVENT,
					tenantId);
			int size = connectionNos.size() + meteredConnectionNos.size();
			message = messageMap.get(WSCalculationConstant.MSG_KEY);
			message = message.replace("{billing cycle}", billingCycle);
			int nmSize = connectionNos.size() - failedConnectionNos.size();
			message = message.replace("{X}", String.valueOf(nmSize)); // this should be x- failed
																		// connections count
			message = message.replace("{X/X+Y}", String.valueOf(connectionNos.size()) + "/" + String.valueOf(size));
			message = message.replace("{Y}", String.valueOf(meteredConnectionNos.size()));
			additionals.put("localizationCode", WSCalculationConstant.NEW_BULK_DEMAND_EVENT);
			HashMap<String, String> attributes = new HashMap<String, String>();
			attributes.put("{billing cycle}", billingCycle);
			attributes.put("{X}", String.valueOf(nmSize)); // this should be x- failed connections
															// count
			attributes.put("{X/X+Y}", String.valueOf(connectionNos.size()) + "/" + String.valueOf(size));
			attributes.put("{Y}", String.valueOf(meteredConnectionNos.size()));
			additionals.put("attributes", attributes);
		} else if (connectionNos.size() > 0 && meteredConnectionNos.isEmpty()) {
			messageMap = util.getLocalizationMessage(requestInfo, WSCalculationConstant.NEW_BULK_DEMAND_EVENT_NM,
					tenantId);
			int nmSize = connectionNos.size() - failedConnectionNos.size();
			message = messageMap.get(WSCalculationConstant.MSG_KEY);
			message = message.replace("{billing cycle}", billingCycle);
			message = message.replace("{X}", String.valueOf(nmSize)); // this should be x- failed
																		// connections count
			message = message.replace("{X/X}",
					String.valueOf(connectionNos.size()) + "/" + String.valueOf(connectionNos.size()));

			additionals.put("localizationCode", "NEW_BULK_DEMAND_EVENT");
			HashMap<String, String> attributes = new HashMap<String, String>();
			attributes.put("{billing cycle}", billingCycle);
			attributes.put("{X}", String.valueOf(nmSize)); // this should be x- failed connections
															// count
			attributes.put("{X/X}", String.valueOf(connectionNos.size()) + "/" + String.valueOf(connectionNos.size()));
			additionals.put("attributes", attributes);
		} else if (connectionNos.isEmpty() && meteredConnectionNos.size() > 0) {
			messageMap = util.getLocalizationMessage(requestInfo, WSCalculationConstant.NEW_BULK_DEMAND_EVENT_M,
					tenantId);
			message = messageMap.get(WSCalculationConstant.MSG_KEY);
			message = message.replace("{Y}", String.valueOf(meteredConnectionNos.size()));
			additionals.put("localizationCode", WSCalculationConstant.NEW_BULK_DEMAND_EVENT);
			HashMap<String, String> attributes = new HashMap<String, String>();
			attributes.put("{Y}", String.valueOf(meteredConnectionNos.size()));
			additionals.put("attributes", attributes);
		}

		System.out.println("Bulk Event msg1:: " + message);
		events.add(Event.builder().tenantId(tenantId).description(message)
				.eventType(WSCalculationConstant.USREVENTS_EVENT_TYPE)
				.name(WSCalculationConstant.MONTHLY_DEMAND_GENERATED)
				.postedBy(WSCalculationConstant.USREVENTS_EVENT_POSTEDBY).recepient(getRecepient(requestInfo, tenantId))
				.source(Source.WEBAPP).eventDetails(null).actions(action).build());

		if (!CollectionUtils.isEmpty(events)) {
			EventRequest eventReq = EventRequest.builder().requestInfo(requestInfo).events(events).build();
			util.sendEventNotification(eventReq);
		}

		// GP User message

		HashMap<String, String> demandMessage = util.getLocalizationMessage(requestInfo,
				WSCalculationConstant.mGram_Consumer_NewDemand, tenantId);

		HashMap<String, String> gpwscMap = util.getLocalizationMessage(requestInfo, tenantId, tenantId);
		UserDetailResponse userDetailResponse = userService.getUserByRoleCodes(requestInfo,
				Arrays.asList("COLLECTION_OPERATOR"), tenantId);
		Map<String, String> mobileNumberIdMap = new LinkedHashMap<>();

		String msgLink = config.getNotificationUrl() + config.getGpUserDemandLink();

		for (OwnerInfo userInfo : userDetailResponse.getUser())
			if (userInfo.getName() != null) {
				mobileNumberIdMap.put(userInfo.getMobileNumber(), userInfo.getName());
			} else {
				mobileNumberIdMap.put(userInfo.getMobileNumber(), userInfo.getUserName());
			}
		mobileNumberIdMap.entrySet().stream().forEach(map -> {
			String msg = demandMessage.get(WSCalculationConstant.MSG_KEY);
			msg = msg.replace("{ownername}", map.getValue());
			msg = msg.replace("{villagename}",
					(gpwscMap != null && !StringUtils.isEmpty(gpwscMap.get(WSCalculationConstant.MSG_KEY)))
							? gpwscMap.get(WSCalculationConstant.MSG_KEY)
							: tenantId);
			msg = msg.replace("{billingcycle}", billingCycle);
			msg = msg.replace("{LINK}", msgLink);

			System.out.println("Demand GP USER SMS1::" + msg);

			SMSRequest smsRequest = SMSRequest.builder().mobileNumber(map.getKey()).message(msg)
					.category(Category.TRANSACTION).build();

			producer.push(config.getSmsNotifTopic(), smsRequest);
		});

	}

	/**
	 * 
	 * @param billingFrequency Billing Frequency details
	 * @param dayOfMonth       Day of the given month
	 * @return true if current day is for generation of demand
	 */
	private boolean isCurrentDateIsMatching(String billingFrequency, long dayOfMonth) {
		if (billingFrequency.equalsIgnoreCase(WSCalculationConstant.Monthly_Billing_Period)
				&& (dayOfMonth == LocalDateTime.now().getDayOfMonth())) {
			return true;
		} else if (billingFrequency.equalsIgnoreCase(WSCalculationConstant.Quaterly_Billing_Period)) {
			return false;
		}
		return true;
	}

	private Recipient getRecepient(RequestInfo requestInfo, String tenantId) {
		Recipient recepient = null;
		UserDetailResponse userDetailResponse = userService.getUserByRoleCodes(requestInfo, Arrays.asList("GP_ADMIN"),
				tenantId);
		if (userDetailResponse.getUser().isEmpty())
			log.error("Recepient is absent");
		else {
			List<String> toUsers = userDetailResponse.getUser().stream().map(OwnerInfo::getUuid)
					.collect(Collectors.toList());

			recepient = Recipient.builder().toUsers(toUsers).toRoles(null).build();
		}
		return recepient;
	}

	@SuppressWarnings("unchecked")
	@KafkaListener(topics = {
			"${egov.generate.bulk.demand.manually.topic}" }, containerFactory = "kafkaListenerContainerFactory")
	public void generateBulkDemandForULB(HashMap<Object, Object> messageData) {
		log.info("Billing master data values for non metered connection:: {}", messageData);
		Map<String, Object> master;
		BulkDemand bulkDemand;
		boolean isSendMessage = false;
		boolean isManual = true;
		HashMap<Object, Object> demandData = (HashMap<Object, Object>) messageData;
		master = (Map<String, Object>) demandData.get("billingMasterData");
		bulkDemand = mapper.convertValue(demandData.get("bulkDemand"), BulkDemand.class);

		String billingPeriod = bulkDemand.getBillingPeriod();
		if (StringUtils.isEmpty(billingPeriod))
			throw new CustomException("BILLING_PERIOD_PARSING_ISSUE", "Billing can not empty!!");

		List<String> connectionNos = waterCalculatorDao.getConnectionsNoList(bulkDemand.getTenantId(),
				WSCalculationConstant.nonMeterdConnection);
		List<String> meteredConnectionNos = waterCalculatorDao.getConnectionsNoList(bulkDemand.getTenantId(),
				WSCalculationConstant.meteredConnectionType);
		SendNotificationsToUsers(bulkDemand.getRequestInfo(), bulkDemand.getTenantId(), billingPeriod, master,
				isSendMessage, isManual);
		Set<String> connectionSet = connectionNos.stream().collect(Collectors.toSet());
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
		Date billingStrartDate;
		Calendar startCal = Calendar.getInstance();
		Calendar endCal = Calendar.getInstance();
		try {
			billingStrartDate = sdf.parse(billingPeriod.split("-")[0].trim());
			Date billingEndDate = sdf.parse(billingPeriod.split("-")[1].trim());
			startCal.setTime(billingStrartDate);
			endCal.setTime(billingEndDate);

		} catch (CustomException | ParseException ex) {
			log.error("", ex);

			if (ex instanceof CustomException)
				throw new CustomException("BILLING_PERIOD_ISSUE", "Billing period can not be in future!!");

			throw new CustomException("BILLING_PERIOD_PARSING_ISSUE", "Billing period can not parsed!!");
		}
		wsCalculationValidator.validateBulkDemandBillingPeriod(startCal.getTimeInMillis(), connectionSet,
				bulkDemand.getTenantId(), (String) master.get(WSCalculationConstant.Billing_Cycle_String));
		
		
		
		
//		String assessmentYear = estimationService.getAssessmentYear();
//		ArrayList<String> failedConnectionNos = new ArrayList<String>();
//
//		for (String connectionNo : connectionNos) {
//			CalculationCriteria calculationCriteria = CalculationCriteria.builder().tenantId(bulkDemand.getTenantId())
//					.assessmentYear(assessmentYear).connectionNo(connectionNo).from(startCal.getTimeInMillis())
//					.to(endCal.getTimeInMillis()).build();
//			List<CalculationCriteria> calculationCriteriaList = new ArrayList<>();
//			calculationCriteriaList.add(calculationCriteria);
//			CalculationReq calculationReq = CalculationReq.builder().calculationCriteria(calculationCriteriaList)
//					.requestInfo(bulkDemand.getRequestInfo()).isconnectionCalculation(true).build();
////			wsCalculationProducer.push(config.getCreateDemand(), calculationReq);
//			// log.info("Prepared Statement" + calculationRes.toString());
//			try {
//				generateDemandInBatch(calculationReq, master, billingPeriod, isSendMessage);
//
//			} catch (Exception e) {
//				// TODO: handle exception
//				failedConnectionNos.add(connectionNo);
//			}
//		}
	}

}
