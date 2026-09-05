#!/usr/bin/env bash
# Переключить основную модель ZCode CLI (~/.zcode/cli/config.json) с бэкапом.
#   ./zcode-model.sh                → показать текущую
#   ./zcode-model.sh glm-5.3-flash  → ночное безлимитное окно (23:00–09:00 UTC+8 = 18:00–04:00 Киев)
#   ./zcode-model.sh glm-5.3        → сильнейшая, off-peak 1× кредитов
#   ./zcode-model.sh glm-5.1        → как после логина
#   ./zcode-model.sh restore        → вернуть бэкап
set -eu
C="$HOME/.zcode/cli/config.json"; B="$HOME/.zcode/cli/config.json.bak"
[ -f "$C" ] || { echo "нет $C — сначала zcode login"; exit 1; }
case "${1:-}" in
  "") python3 -c "import json;d=json.load(open('$C'));print('main =',d['model']['main'],'| lite =',d['model'].get('lite'))";;
  restore) [ -f "$B" ] && cp "$B" "$C" && echo "восстановлено из $B" || echo "бэкапа нет";;
  *) [ -f "$B" ] || cp "$C" "$B"
     python3 - "$1" <<'PY'
import json,sys,os
m=sys.argv[1]; p=os.path.expanduser('~/.zcode/cli/config.json'); d=json.load(open(p))
d['provider']['zai'].setdefault('models',{}).setdefault(m,{'name':m.upper()}); d['model']['main']='zai/'+m
json.dump(d,open(p,'w'),ensure_ascii=False,indent=1); print('main = zai/'+m)
PY
     ;;
esac
