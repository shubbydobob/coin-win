#!/usr/bin/env bash
# domain 패키지에 프레임워크 의존이 들어가는 것을 파일이 써지기 전에 차단한다.
# .claude/settings.json 의 PreToolUse 훅에서 호출.
# exit 2 -> 도구 실행 차단, stderr 메시지가 Claude에게 전달됨.

set -uo pipefail

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | grep -o '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/.*"\([^"]*\)"$/\1/')

case "$FILE_PATH" in
  */domain/*.java) ;;
  *) exit 0 ;;
esac

# Spring Boot 4 는 Jackson 3(tools.jackson)이 기본이고 Jackson 2(com.fasterxml.jackson)가 병행이다.
# 둘 다 막지 않으면 구멍이 뚫린다. ArchUnit 규칙 1(ArchitectureRules#domainIsFrameworkFree)과 동일한 목록.
FORBIDDEN=(
  "org.springframework"
  "jakarta.persistence"
  "com.fasterxml.jackson"
  "tools.jackson"
  "io.swagger"
  "org.hibernate"
)

for pkg in "${FORBIDDEN[@]}"; do
  if echo "$INPUT" | grep -q "$pkg"; then
    {
      echo "차단: domain 패키지는 프레임워크에 의존할 수 없습니다."
      echo "  파일: $FILE_PATH"
      echo "  금지된 의존: $pkg"
      echo ""
      echo "  이 관심사는 application 또는 adapter 계층에 두세요."
      echo "  영속성이 필요하면 adapter/out/persistence 에 별도 엔티티를 만들고"
      echo "  도메인 객체와 매핑하세요."
    } >&2
    exit 2
  fi
done

exit 0
