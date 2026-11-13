/*
 * Copyright (C) Bekk
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
import React from 'react';
import CodeMirror from '@uiw/react-codemirror';
import { sql } from '@codemirror/lang-sql';
import { Box } from '@chakra-ui/react';
import colors from 'src/styles/colors';

interface ScriptEditorProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  height?: string;
}

const ScriptEditor: React.FC<ScriptEditorProps> = ({
  value,
  onChange,
  placeholder,
  height = '280px',
}) => (
  <Box
    borderWidth={1}
    borderColor={colors.primary[300]}
    borderRadius={4}
    overflow={'hidden'}
    fontSize={'sm'}
  >
    <CodeMirror
      value={value}
      height={height}
      extensions={[sql()]}
      placeholder={placeholder}
      onChange={onChange}
      basicSetup={{
        lineNumbers: true,
        foldGutter: false,
        highlightActiveLine: false,
      }}
    />
  </Box>
);

export default ScriptEditor;
