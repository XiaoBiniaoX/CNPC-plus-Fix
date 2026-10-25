#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
from pathlib import Path

def main():
    # 获取脚本所在的目录
    script_dir = Path(__file__).parent.resolve()
    output_file = script_dir / "file_list.txt"

    # 递归收集所有文件
    files = []
    for item in script_dir.rglob('*'):
        if item.is_file():
            # 排除输出文件本身（如果已存在），避免自引用
            if item.resolve() == output_file.resolve():
                continue
            # 使用相对于脚本目录的路径
            rel_path = item.relative_to(script_dir)
            files.append(str(rel_path))

    # 按路径排序，便于阅读
    files.sort()

    # 写入文件（UTF-8编码）
    output_file.write_text("\n".join(files), encoding='utf-8')

    # 控制台提示
    print(f"文件列表已生成：{output_file}")

if __name__ == "__main__":
    main()