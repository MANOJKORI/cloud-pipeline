/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {Button, Icon} from 'antd';
import styles from './pickers.css';

class PickerButton extends React.Component {
  static propTypes = {
    className: PropTypes.string,
    children: PropTypes.node,
    onClick: PropTypes.func,
    onRemove: PropTypes.func,
    valueIsSet: PropTypes.bool,
    style: PropTypes.shape()
  };

  state = {
    hovered: false
  };

  handleHover = (hover) => {
    this.setState({hovered: hover});
  };

  onRemove = (e) => {
    e.stopPropagation();
    e.preventDefault();
    const {onRemove} = this.props;
    if (onRemove) {
      onRemove();
    }
  };

  render () {
    const {
      className,
      children,
      onClick,
      valueIsSet,
      style,
      onRemove
    } = this.props;
    const {hovered} = this.state;
    const onRemoveIsSet = !!onRemove;
    const classNames = [
      valueIsSet ? styles.valueIsSet : undefined,
      hovered ? styles.hovered : undefined
    ].filter(Boolean).join(' ');
    return (
      <Button
        className={className}
        onClick={onClick}
        style={style}
        onMouseOver={() => this.handleHover(true)}
        onMouseOut={() => this.handleHover(false)}
      >
        <div className={classNames}>
          {children || 'Calendar'}
          {
            valueIsSet &&
            onRemoveIsSet &&
            hovered && (
              <Icon
                className={styles.close}
                type="close-circle"
                onClick={this.onRemove}
              />
            )
          }
          {(!valueIsSet || !hovered || !onRemoveIsSet) && <Icon type="calendar" />}
        </div>
      </Button>
    );
  }
}

export default PickerButton;
