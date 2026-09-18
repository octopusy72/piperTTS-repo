package com.yiwoosolution.piperprototype;

import java.util.Arrays;
import java.util.List;

/** Local, raw-notation fixtures for Voice Test; pronunciation is left to the pipeline. */
final class VoiceTestPresetCatalog {
  private VoiceTestPresetCatalog() {}

  static List<MainActivity.Preset> korean() {
    return Arrays.asList(
        p("기초 검증", "문장 끝과 조사", "테스트. 테스트를 진행합니다. 테스트가 끝났습니다. 테스트 결과를 확인합니다. 컴퓨터 사용. 컴퓨터 사용 중입니다. 이것은 컴퓨터입니다."),
        p("기초 검증", "수량과 식별자 구분", "참석자는 1000명이고 안건은 3가지입니다. 가격은 1000원입니다. KE1000편과 KE007편을 확인합니다. 장비 번호는 SM-A346N입니다."),
        p("금액 검증", "달러와 소수 금액", "결제 금액은 $45.14입니다. 배송비는 $0.50이고 총액은 USD 100.05입니다. 환불 금액은 $1.01입니다."),
        p("금액 검증", "다국가 통화", "견적은 ₩45,000, €45.14, £12.50, ¥1500입니다. 각 통화의 금액과 소수 부분을 비교합니다."),
        p("일시 검증", "날짜 표기와 요일", "회의일은 2026-09-17 목요일입니다. 2026/09/18 금요일에 재확인합니다. 미국식 09/17/2026과 03/09/2026 표기를 비교합니다. Monday부터 Friday까지 운영합니다."),
        p("일시 검증", "시간과 경기 점수", "지금 시간은 03:02입니다. 경기는 3:2로 이겼습니다. 다음 회의는 오후 3:30이며 마감은 15:45입니다. 오전 12:00과 오후 12:00을 비교합니다."),
        p("일시 검증", "기간과 분기", "다음 회의는 3-5일 뒤 열립니다. 작업은 3~5일간 진행합니다. 1/4분기, 2/4분기, 3/4분기, 4/4분기 실적을 확인합니다."),
        p("단위 검증", "속도와 온도", "차량이 시속 80km/h로 달립니다. 바람의 속도는 4m/s입니다. 현재 온도는 3.5℃입니다. 섭씨 25°C와 화씨 68°F를 비교합니다."),
        p("단위 검증", "범위와 단위", "다음 열차는 10~20분 뒤 도착합니다. 내일은 10~20mm의 비가 예상됩니다. 우회전한 뒤 10~20m 앞에서 정차합니다."),
        p("수식 검증", "사칙연산과 지수", "2 + 3 = 5. 12 × 4 = 48. 10 ÷ 2 = 5. x² + y² = 25. x³. 10^4. 2^6. A > B, x ≤ 10 조건을 확인합니다."),
        p("IT 검증", "네트워크 주소", "IPv4 주소는 192.168.1.2입니다. IPv6 설정도 확인합니다. 안내는 https://section.cafe.naver.com/ca-fe/home에 있습니다. 연락처는 user@example.com입니다."),
        p("IT 검증", "버전과 모델명", "버전 v2.1.3, v02.01.03, 버전 3.1.03을 비교합니다. Android 16, API-36, RTX-5070, USB 3.2, HDMI 2.1, Wi-Fi 7을 확인합니다."),
        p("혼합 검증", "외래어와 사용자 규칙", "YouTube에서 notification 설정을 확인하고 GTX-A를 이용하세요. USB-C 충전기를 연결합니다. Google과 Microsoft 계정을 확인합니다. 결제 금액은 $45.14입니다."),
        p("혼합 검증", "채팅과 기호", "안녕하세요! 정말요? ㅋㅋㅋ. ㅎㅎㅎ. 확인했습니다 :) 다음에 만나요. 입력/출력과 라인 A/B를 확인합니다."),
        p("금융", "계좌와 이체", "오늘 원/달러 환율은 1,387.50원입니다. 계좌 잔액은 ₩3,250,000이고 이번 달 카드 사용액은 1,284,300원입니다. 연 3.75% 금리 대출 원금 중 1/3을 먼저 상환합니다. OTP 인증 후 15,000원을 송금해 주세요."),
        p("숫자와 단위", "측정값", "실험실까지 거리는 1,200km이고 차량 속도는 80km/h입니다. 상자 무게는 21.5kg, 저장 용량은 256GB, 시료 온도는 3.5℃입니다. 남은 액체는 500mL입니다."),
        p("수학", "계산과 수식", "오늘은 1/3과 3/4의 크기를 비교합니다. 2 + 3 = 5이고 12 × 4 = 48입니다. 10 ÷ 2 = 5도 확인합니다. x² + y² = 25이고 A > B, x ≤ 10 조건을 적용합니다."),
        p("택배", "배송 안내", "주문번호 A-20260916-372 상품이 오늘 배송됩니다. 현재 상태는 배송 중이며 예상 도착 시간은 오후 3:30~5:00입니다. 상품 무게는 2.5kg이고 배송비 3,500원은 결제되었습니다. 배송이 완료되면 앱에 알림이 표시됩니다 📦."),
        p("공장", "생산 현황", "오늘 생산량은 13,000개이고 불량률은 0.8%입니다. 2번 라인은 오후 10:00에 점검을 시작합니다. 생산 온도는 21.5℃로 유지하며 라인 A/B의 속도는 3.2m/s입니다. 보고서 버전은 v2.1.3입니다."),
        p("공항", "탑승 안내", "KE123편은 오늘 18:35에 출발하고 탑승구는 Gate A12입니다. 위탁 수하물은 1인당 23kg까지 허용됩니다. Group 1부터 탑승하며 노트북과 USB-C 충전기는 기내에 넣어 주세요. 무료 Wi-Fi는 1시간 이용할 수 있습니다."),
        p("철도", "열차 안내", "GTX-A 열차가 5분 후 2번 승강장에 도착합니다. 서울역에서 3호선으로 환승해 주세요. 다음 열차는 10~20분 뒤 출발하며 운행 속도는 80km/h입니다. 승차권 QR 코드를 준비해 주세요."),
        p("병원", "예약 안내", "진료 예약은 2026년 9월 16일 오전 8:30입니다. 체온은 36.5℃이고 혈압 비율은 1:2로 기록되었습니다. 처방약은 하루 2회 복용하며 다음 검사는 3/4분기에 진행합니다. 문의는 02-1234-5678로 연락해 주세요."),
        p("날씨", "일기예보", "오늘 서울 기온은 21.5℃이고 비 올 확률은 40%입니다. 오후 3:30부터 바람이 5m/s로 붑니다. 내일은 10∼20mm의 비가 예상됩니다. 날씨가 맑으면 산책을 하세요 ☀️."),
        p("뉴스", "주요 소식", "정부는 새로운 교통 정책을 발표했습니다. 전국 평균 물가는 2.3% 상승했고 투표율은 75%였습니다. 정책 시행일은 2026-09-16이며 관련 자료는 https://example.com/news에서 확인할 수 있습니다."),
        p("일정", "날짜와 시간", "회의는 2026년 9월 16일 오후 3:30에 시작합니다. 09/16 일정은 오전 8:30에 다시 확인해 주세요. 준비 자료는 15:45까지 보내고 다음 회의는 3-5일 뒤 열립니다. 캘린더 알림을 켜 두세요."),
        p("쇼핑", "주문과 결제", "주문 금액은 ₩128,000이고 배송비는 3,500원입니다. 쿠폰 할인율은 12.5%이며 결제 예정일은 2026년 9월 18일입니다. 상품은 3일 이내 도착하고 문의 번호는 010-1234-5678입니다."),
        p("연락처", "주소와 전화번호", "배송지는 서울특별시 중구 세종대로 110, 3층 4호입니다. 문의는 02-1234-5678 또는 user@example.com으로 보내 주세요. 서버 주소는 192.168.0.1이고 안내 페이지는 https://example.com/a/b입니다."),
        p("IT", "기기 안내", "현재 CPU 사용률은 85%이고 RAM은 1.8GB입니다. Wi-Fi 6과 Bluetooth를 사용하며 IP 주소는 192.168.0.1입니다. USB-C 케이블을 연결하고 Android Version 2.1.3을 설치합니다. RTF는 0.48입니다."),
        p("자동차", "내비게이션", "목적지까지 12.5km 남았고 제한 속도는 60km/h입니다. 다음 교차로에서 우회전한 뒤 10~20m 앞에서 차선을 변경하세요. 내비게이션 버전은 v2.1.3이며 경로 ID는 NAV-2026-0916입니다."),
        p("안전", "재난 안내", "화재가 발생하면 엘리베이터를 사용하지 말고 비상 계단으로 이동하세요. 119에 신고하고 안내 방송을 따라 주세요. 대피 경로는 A/B로 나뉘며 집결 시간은 5분입니다. 주의가 필요합니다 ⚠️."),
        p("교육", "수업 안내", "오늘 수업에서는 1/2과 3/4 분수를 비교하고 2 + 3 = 5 계산을 연습합니다. 숙제는 20쪽까지 읽고 2026년 9월 16일에 제출하세요. USB-C 태블릿으로 실험 결과를 기록합니다."),
        p("업무", "회의 일정", "이번 주 매출은 지난주보다 12% 증가했습니다. 팀 회의는 내일 오전 9:00에 시작하고 안건은 3가지입니다. 자료는 Version 2.1 형식으로 저장하며 담당자는 support@example.com으로 회신합니다."),
        p("일상", "생활 대화", "오늘은 맑아서 커피 한 잔을 마시고 산책을 다녀오겠습니다. 장보기 목록에는 1.5kg 사과와 500mL 우유가 있습니다. 배송이 완료되었습니다 📦. 저녁에는 가족과 영화를 볼 예정입니다."),
        p("종합", "스트레스 테스트", "2026년 9월 16일 오후 3:30에 서울역 3번 출구에서 만나요. 여행 거리는 1,200km이고 예상 비용은 ₩3,250,000입니다. 1/3은 먼저 결제하고 나머지 3/4은 카드로 결제합니다. 기온은 21.5℃, 속도는 80km/h이며 USB-C, CPU, AI, GTX-A, v2.1.3, 192.168.0.1, user@example.com, https://example.com을 함께 확인합니다. 배송이 완료되었습니다 📦.")
    );
  }

  static List<MainActivity.Preset> english() {
    return Arrays.asList(
        p("Focused checks", "Currency and cents", "The total is $45.14. Shipping costs $0.50. Compare USD 100.05, EUR 45.14, and GBP 12.50."),
        p("Focused checks", "Dates and times", "The meeting is on September 17, 2026, at 3:02 PM. Compare 09/17/2026 and 2026-09-17. Office hours are Monday through Friday, 9:00 AM to 5:30 PM."),
        p("Focused checks", "Versions and identifiers", "Check version v2.1.3 and v02.01.03. Flight KE007 uses gate A34. Connect the USB-C adapter to the SM-A346N device."),
        p("Focused checks", "Addresses and measurements", "Visit https://example.com/a/b or email user@example.com. The IP address is 192.168.1.2. Compare 25°C, 68°F, 80km/h, and 4m/s."),
        p("Finance", "Account transfer", "The exchange rate is 1,387.50 won today. The balance is $3,250,000, and the scheduled payment is $2,350.75. Please transfer 15,000 won after OTP verification. The annual interest rate is 3.75%, and 1/3 of the principal is due first."),
        p("Numbers & Measurements", "Measurements", "The factory is 1,200 kilometers away. The package weighs 21.5 kilograms and holds 256GB. The temperature is 21.5°C, and the liquid volume is 500mL. The measured speed is 80km/h."),
        p("Mathematics", "Calculations", "Compare 1/3 with 3/4 in this example. 2 + 3 = 5, 12 × 4 = 48, and 10 ÷ 2 = 5. The equation is x² + y² = 25. Use the conditions A > B and x ≤ 10."),
        p("Delivery", "Shipping update", "Order A-20260916-372 is out for delivery. The estimated arrival window is 3:30 PM to 5:00 PM. The package weighs 2.5kg and the shipping fee is $3.50. Delivery is complete when the app shows 📦."),
        p("Manufacturing", "Production report", "Today we produced 13,000 units with a defect rate of 0.8%. Line A and Line B will stop at 10:00 PM. Keep the temperature at 21.5°C and save the report as Version 2.1.3."),
        p("Airport", "Boarding announcement", "Flight KE123 departs at 6:35 PM from Gate A12. One checked bag up to 23kg is included. Group 1 boards first. Please keep your laptop and USB-C charger in your carry-on bag."),
        p("Train & Transit", "Train announcement", "The GTX-A train arrives at platform 2 in five minutes. Transfer to Line 3 at Seoul Station. The next train leaves in 10 to 20 minutes. Please have your QR ticket ready."),
        p("Hospital", "Appointment reminder", "Your appointment is on September 16, 2026, at 8:30 AM. Your temperature is 36.5°C. Take the medicine twice a day, and return for the next test in three to four weeks. Call 02-1234-5678 for help."),
        p("Weather", "Forecast", "The temperature in Seoul is 21.5°C with a 40% chance of rain. Winds will reach 5m/s after 3:30 PM. Rainfall may range from 10 to 20mm tomorrow. The weather is sunny today ☀️."),
        p("News", "Daily briefing", "The government announced a new policy. Consumer prices rose by 2.3%, and approval reached 75%. The policy starts on 2026-09-16. Read the full report at https://example.com/news."),
        p("Dates & Scheduling", "Calendar", "The meeting starts on September 16th, 2026, at 3:30 PM. The reminder is set for 09/16 at 8:30 AM. Send the materials by 15:45, and schedule the next meeting in 3-5 days."),
        p("Shopping & Payment", "Order confirmation", "Your order total is $128.00 and shipping is $3.50. The coupon saves 12.5%. Payment is due on September 18th, 2026, and delivery should arrive within three days. Your receipt number is 010-1234-5678."),
        p("Address & Phone", "Contact details", "The delivery address is 110 Sejong-daero, Jung-gu, Seoul. Email user@example.com or call 02-1234-5678. The server address is 192.168.0.1, and the guide is at https://example.com/a/b."),
        p("IT & Devices", "Device instructions", "CPU usage is 85%, and available RAM is 1.8GB. Connect to Wi-Fi 6 and Bluetooth. Plug in the USB-C cable and install Android Version 2.1.3. The current RTF is 0.48."),
        p("Navigation", "Driving directions", "You have 12.5km remaining, and the speed limit is 60km/h. Turn right at the next intersection and change lanes in 10 to 20 meters. The route ID is NAV-2026-0916."),
        p("Emergency & Safety", "Emergency guidance", "If there is a fire, do not use the elevator. Move to the emergency stairs and call 911. Follow route A or route B to the assembly point in five minutes. Please be careful ⚠️."),
        p("Education", "Class announcement", "Today we will compare 1/2 and 3/4 and practice 2 + 3 = 5. Read page 20 and submit the assignment on September 16, 2026. Record the experiment on the USB-C tablet."),
        p("Business & Meetings", "Business update", "This week's sales increased by 12%. The team meeting starts at 9:00 AM tomorrow with three agenda items. Save the document as Version 2.1 and reply to support@example.com."),
        p("Daily Conversation", "Everyday plans", "The weather is clear today, so I will have coffee and take a walk. The shopping list includes 1.5kg of apples and 500mL of milk. The package arrived 📦, and we will watch a movie tonight."),
        p("Comprehensive", "Stress test", "Meet me at Exit 3 of Seoul Station on September 16th, 2026, at 3:30 PM. The trip is 1,200km and the budget is $3,250,000. Pay 1/3 first and the remaining 3/4 by card. Check 21.5°C, 80km/h, USB-C, CPU, AI, GTX-A, version 2.1.3, 192.168.0.1, user@example.com, and https://example.com. Delivery is complete 📦.")
    );
  }

  private static MainActivity.Preset p(String category, String title, String text) {
    return new MainActivity.Preset(category, title, text);
  }
}
