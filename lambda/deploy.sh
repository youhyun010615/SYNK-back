#!/bin/bash
# synk-collage Lambda 배포 스크립트
# Pillow(이미지 합성용)를 매번 다시 받아서 패키징 + 배포한다.
set -e
cd "$(dirname "$0")"

rm -rf package lambda_function.zip
mkdir package

pip3 install --platform manylinux2014_x86_64 --target=package \
  --implementation cp --python-version 3.11 --only-binary=:all: Pillow

cp lambda_function.py package/
cp NanumGothic.ttc package/

cd package
zip -r -X ../lambda_function.zip . -x ".*" > /dev/null
cd ..

aws lambda update-function-code \
  --function-name synk-collage \
  --zip-file fileb://lambda_function.zip \
  --region ap-northeast-2 \
  --query 'FunctionName' --output text

rm -rf package
echo "배포 완료"
