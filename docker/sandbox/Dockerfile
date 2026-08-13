# DataRobort Python 沙箱镜像
# 构建: docker build -t datarobort-sandbox:latest -f docker/sandbox/Dockerfile .
FROM python:3.12-slim

# 数据分析核心库（国内环境使用清华 PyPI 镜像加速，官方源易超时）
# 如需官方源：删除 -i 参数即可
RUN pip install --no-cache-dir \
    -i https://pypi.tuna.tsinghua.edu.cn/simple \
    --timeout 120 --retries 10 \
    pandas==2.2.3 \
    numpy==2.2.1 \
    matplotlib==3.9.3

# 安全: 用非 root 用户运行
RUN useradd --create-home --shell /bin/bash sandbox
USER sandbox
WORKDIR /home/sandbox

# stdin 模式运行 python（配合 docker run -i python -）
ENTRYPOINT ["python", "-"]
