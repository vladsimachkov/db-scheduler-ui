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
import { Text } from '@chakra-ui/react';
import React from 'react';

type JsonViewerProps = {
  data: object | null;
};

const JsonViewer: React.FC<JsonViewerProps> = ({ data }) => {
  const renderJson = (data: object | null, indent: number = 0) => {
    if (typeof data === 'object' && data !== null) {
      return (
        <div style={{ paddingLeft: `${indent * 20}px` }}>
          {Array.isArray(data)
            ? data.map((item, index) => (
                <div key={index}>{renderJson(item, indent + 1)}</div>
              ))
            : Object.entries(data).map(([key, value]) => (
                <div key={key}>
                  <Text as="span" color="textMuted">{key}:</Text>{' '}
                  {typeof value === 'object' ? (
                    renderJson(value, indent + 1)
                  ) : (
                    <Text as="span" color="textStrong">
                      {JSON.stringify(value)}
                    </Text>
                  )}
                </div>
              ))}
        </div>
      );
    } else {
      return <span>{JSON.stringify(data)}</span>;
    }
  };

  return <pre>{renderJson(data)}</pre>;
};

export default JsonViewer;
